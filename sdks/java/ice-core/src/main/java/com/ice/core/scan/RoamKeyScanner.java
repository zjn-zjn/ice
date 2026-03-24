package com.ice.core.scan;

import com.ice.common.model.LeafNodeInfo.KeyPart;
import com.ice.common.model.LeafNodeInfo.RoamKeyMeta;
import org.objectweb.asm.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;

/**
 * Scans leaf node bytecode to extract roam key access metadata.
 * Uses ASM to analyze doResult/doFlow/doNone methods for IceRoam get/put calls.
 *
 * @author waitmoon
 */
public final class RoamKeyScanner {

    private static final Logger log = LoggerFactory.getLogger(RoamKeyScanner.class);

    private static final String ROAM_INTERNAL_NAME = "com/ice/core/context/IceRoam";
    private static final Set<String> TARGET_METHODS = new HashSet<>(Arrays.asList("doFlow", "doResult", "doNone"));
    private static final int MAX_DEPTH = 10;

    // Roam method name -> (direction, accessMode, accessMethod)
    private static final Map<String, String[]> ROAM_READ_METHODS = new HashMap<>();
    private static final Map<String, String[]> ROAM_WRITE_METHODS = new HashMap<>();

    static {
        ROAM_READ_METHODS.put("get", new String[]{"read", "direct", "get"});
        ROAM_READ_METHODS.put("getValue", new String[]{"read", "direct", "get"});
        ROAM_READ_METHODS.put("getDeep", new String[]{"read", "direct", "getDeep"});
        ROAM_READ_METHODS.put("resolve", new String[]{"read", "union", "get"});
        ROAM_WRITE_METHODS.put("put", new String[]{"write", "direct", "put"});
        ROAM_WRITE_METHODS.put("putValue", new String[]{"write", "direct", "put"});
        ROAM_WRITE_METHODS.put("putDeep", new String[]{"write", "direct", "putDeep"});
    }

    private RoamKeyScanner() {
    }

    public static List<RoamKeyMeta> scan(Class<?> leafClass) {
        try {
            return doScan(leafClass, new HashSet<>(), 0);
        } catch (Exception e) {
            log.debug("failed to scan roam keys for {}: {}", leafClass.getName(), e.getMessage());
            return Collections.emptyList();
        }
    }

    private static List<RoamKeyMeta> doScan(Class<?> clazz, Set<String> visited, int depth) throws IOException {
        String internalName = clazz.getName().replace('.', '/');
        if (!visited.add(internalName) || depth > MAX_DEPTH) {
            return Collections.emptyList();
        }

        ClassReader cr = readClass(clazz);
        if (cr == null) {
            return Collections.emptyList();
        }

        List<RoamKeyMeta> results = new ArrayList<>();
        // Field name -> field descriptor for resolving GETFIELD references
        Map<String, String> classFields = new HashMap<>();

        cr.accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
                if ((access & Opcodes.ACC_STATIC) == 0 && (access & Opcodes.ACC_FINAL) == 0) {
                    classFields.put(name, descriptor);
                }
                return null;
            }

            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                if (TARGET_METHODS.contains(name)) {
                    // doResult/doFlow/doNone: slot 0 = this, slot 1 = IceRoam roam
                    return new RoamMethodVisitor(Opcodes.ASM9, results, internalName, classFields, clazz, visited, depth, 1);
                }
                return null;
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);

        return mergeDirections(results);
    }

    private static ClassReader readClass(Class<?> clazz) throws IOException {
        String resourceName = clazz.getName().replace('.', '/') + ".class";
        try (InputStream is = clazz.getClassLoader().getResourceAsStream(resourceName)) {
            if (is == null) {
                return null;
            }
            return new ClassReader(is);
        }
    }

    /**
     * Merge RoamKeyMeta entries with same key signature: if same key has both read and write, merge to read_write.
     */
    private static List<RoamKeyMeta> mergeDirections(List<RoamKeyMeta> metas) {
        if (metas.size() <= 1) {
            return metas;
        }

        // Group by key signature (keyParts serialized)
        Map<String, RoamKeyMeta> merged = new LinkedHashMap<>();
        for (RoamKeyMeta meta : metas) {
            String sig = keySignature(meta);
            RoamKeyMeta existing = merged.get(sig);
            if (existing == null) {
                merged.put(sig, meta);
            } else {
                // Merge directions
                if (!existing.getDirection().equals(meta.getDirection())) {
                    existing.setDirection("read_write");
                }
            }
        }
        return new ArrayList<>(merged.values());
    }

    private static String keySignature(RoamKeyMeta meta) {
        StringBuilder sb = new StringBuilder();
        appendKeyParts(sb, meta.getKeyParts());
        return sb.toString();
    }

    private static void appendKeyParts(StringBuilder sb, List<KeyPart> parts) {
        if (parts == null) return;
        for (KeyPart p : parts) {
            sb.append('[').append(p.getType()).append('=');
            if ("literal".equals(p.getType())) sb.append(p.getValue());
            else if ("field".equals(p.getType())) sb.append(p.getRef());
            else if ("roamDerived".equals(p.getType())) sb.append(p.getFromKey());
            else if ("composite".equals(p.getType())) appendKeyParts(sb, p.getParts());
            sb.append(']');
        }
    }

    /**
     * ASM MethodVisitor that tracks the operand stack to resolve roam call arguments.
     */
    private static class RoamMethodVisitor extends MethodVisitor {

        private final List<RoamKeyMeta> results;
        private final String ownerInternalName;
        private final Map<String, String> classFields;
        private final Class<?> leafClass;
        private final Set<String> visited;
        private final int depth;

        // Simple operand stack simulation for tracking key argument sources
        private final Deque<StackEntry> stack = new ArrayDeque<>();
        // Local variable tracking: slot -> source info
        private final Map<Integer, StackEntry> locals = new HashMap<>();
        // Track which local variable slots hold roam references
        private final Set<Integer> roamSlots = new HashSet<>();

        RoamMethodVisitor(int api, List<RoamKeyMeta> results, String ownerInternalName,
                          Map<String, String> classFields, Class<?> leafClass,
                          Set<String> visited, int depth, int roamSlot) {
            super(api);
            this.results = results;
            this.ownerInternalName = ownerInternalName;
            this.classFields = classFields;
            this.leafClass = leafClass;
            this.visited = visited;
            this.depth = depth;
            roamSlots.add(roamSlot);
        }

        @Override
        public void visitLdcInsn(Object value) {
            if (value instanceof String) {
                stack.push(new StackEntry(StackEntryType.LITERAL, (String) value));
            } else {
                stack.push(new StackEntry(StackEntryType.UNKNOWN, null));
            }
        }

        @Override
        public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
            if (opcode == Opcodes.GETFIELD && owner.equals(ownerInternalName)) {
                // this.fieldName -> field reference
                stack.poll(); // pop 'this'
                stack.push(new StackEntry(StackEntryType.FIELD, name));
            } else if (opcode == Opcodes.GETFIELD) {
                stack.poll(); // pop objectref
                stack.push(new StackEntry(StackEntryType.UNKNOWN, null));
            } else if (opcode == Opcodes.GETSTATIC) {
                stack.push(new StackEntry(StackEntryType.UNKNOWN, null));
            } else if (opcode == Opcodes.PUTFIELD || opcode == Opcodes.PUTSTATIC) {
                stack.poll(); // value
                if (opcode == Opcodes.PUTFIELD) stack.poll(); // objectref
            }
        }

        @Override
        public void visitVarInsn(int opcode, int varIndex) {
            if (opcode >= Opcodes.ILOAD && opcode <= Opcodes.ALOAD) {
                StackEntry entry = locals.get(varIndex);
                if (entry != null) {
                    stack.push(entry);
                } else if (roamSlots.contains(varIndex)) {
                    stack.push(new StackEntry(StackEntryType.ROAM_REF, null));
                } else {
                    stack.push(new StackEntry(StackEntryType.UNKNOWN, null));
                }
            } else if (opcode >= Opcodes.ISTORE && opcode <= Opcodes.ASTORE) {
                StackEntry entry = stack.poll();
                if (entry != null) {
                    locals.put(varIndex, entry);
                    if (entry.type == StackEntryType.ROAM_REF) {
                        roamSlots.add(varIndex);
                    }
                }
            }
        }

        @Override
        public void visitInsn(int opcode) {
            switch (opcode) {
                case Opcodes.ACONST_NULL:
                    stack.push(new StackEntry(StackEntryType.UNKNOWN, null));
                    break;
                case Opcodes.DUP:
                    StackEntry top = stack.peek();
                    if (top != null) stack.push(top);
                    else stack.push(new StackEntry(StackEntryType.UNKNOWN, null));
                    break;
                case Opcodes.POP:
                    stack.poll();
                    break;
                case Opcodes.POP2:
                    stack.poll();
                    stack.poll();
                    break;
                case Opcodes.SWAP:
                    StackEntry a = stack.poll();
                    StackEntry b = stack.poll();
                    if (a != null) stack.push(a);
                    if (b != null) stack.push(b);
                    break;
                case Opcodes.ICONST_M1:
                case Opcodes.ICONST_0:
                case Opcodes.ICONST_1:
                case Opcodes.ICONST_2:
                case Opcodes.ICONST_3:
                case Opcodes.ICONST_4:
                case Opcodes.ICONST_5:
                case Opcodes.LCONST_0:
                case Opcodes.LCONST_1:
                case Opcodes.FCONST_0:
                case Opcodes.FCONST_1:
                case Opcodes.FCONST_2:
                case Opcodes.DCONST_0:
                case Opcodes.DCONST_1:
                    stack.push(new StackEntry(StackEntryType.UNKNOWN, null));
                    break;
                case Opcodes.IRETURN:
                case Opcodes.LRETURN:
                case Opcodes.FRETURN:
                case Opcodes.DRETURN:
                case Opcodes.ARETURN:
                    stack.poll();
                    break;
                case Opcodes.IADD: case Opcodes.ISUB: case Opcodes.IMUL: case Opcodes.IDIV:
                case Opcodes.LADD: case Opcodes.LSUB: case Opcodes.LMUL: case Opcodes.LDIV:
                case Opcodes.FADD: case Opcodes.FSUB: case Opcodes.FMUL: case Opcodes.FDIV:
                case Opcodes.DADD: case Opcodes.DSUB: case Opcodes.DMUL: case Opcodes.DDIV:
                    stack.poll();
                    stack.poll();
                    stack.push(new StackEntry(StackEntryType.UNKNOWN, null));
                    break;
                default:
                    // For other arithmetic/conversion operations, approximate
                    break;
            }
        }

        @Override
        public void visitIntInsn(int opcode, int operand) {
            stack.push(new StackEntry(StackEntryType.UNKNOWN, null));
        }

        @Override
        public void visitTypeInsn(int opcode, String type) {
            if (opcode == Opcodes.CHECKCAST) {
                // Don't change stack - the top is still the same value
            } else if (opcode == Opcodes.NEW) {
                stack.push(new StackEntry(StackEntryType.UNKNOWN, null));
            } else if (opcode == Opcodes.INSTANCEOF) {
                stack.poll();
                stack.push(new StackEntry(StackEntryType.UNKNOWN, null));
            } else if (opcode == Opcodes.ANEWARRAY) {
                stack.poll(); // size
                stack.push(new StackEntry(StackEntryType.UNKNOWN, null));
            }
        }

        @Override
        public void visitInvokeDynamicInsn(String name, String descriptor, Handle bootstrapMethodHandle, Object... bootstrapMethodArguments) {
            // Handle string concatenation (makeConcatWithConstants)
            if ("makeConcatWithConstants".equals(name) && bootstrapMethodArguments.length > 0) {
                String recipe = bootstrapMethodArguments[0].toString();
                // Count how many args this consumes
                Type[] argTypes = Type.getArgumentTypes(descriptor);
                List<StackEntry> args = new ArrayList<>();
                for (int i = argTypes.length - 1; i >= 0; i--) {
                    StackEntry e = stack.poll();
                    args.add(0, e != null ? e : new StackEntry(StackEntryType.UNKNOWN, null));
                }
                // Build composite KeyPart from recipe and args
                StackEntry composite = buildCompositeFromRecipe(recipe, args);
                stack.push(composite);
            } else {
                // Unknown invokedynamic - consume args and push unknown
                Type[] argTypes = Type.getArgumentTypes(descriptor);
                for (int i = 0; i < argTypes.length; i++) {
                    stack.poll();
                }
                stack.push(new StackEntry(StackEntryType.UNKNOWN, null));
            }
        }

        @Override
        public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
            Type[] argTypes = Type.getArgumentTypes(descriptor);
            Type returnType = Type.getReturnType(descriptor);

            // Check if this is a roam method call
            if (owner.equals(ROAM_INTERNAL_NAME)) {
                String[] readInfo = ROAM_READ_METHODS.get(name);
                String[] writeInfo = ROAM_WRITE_METHODS.get(name);

                if (readInfo != null && argTypes.length >= 1) {
                    // Read operation: pop args, analyze key
                    List<StackEntry> args = popArgs(argTypes.length);
                    stack.poll(); // pop roam objectref

                    StackEntry keyArg = args.get(0);
                    List<KeyPart> keyParts = toKeyParts(keyArg);
                    if (keyParts != null && !keyParts.isEmpty()) {
                        RoamKeyMeta meta = new RoamKeyMeta();
                        meta.setDirection(readInfo[0]);
                        meta.setAccessMode(readInfo[1]);
                        meta.setAccessMethod(readInfo[2]);
                        meta.setKeyParts(keyParts);
                        StackEntry resultEntry = new StackEntry(StackEntryType.ROAM_DERIVED, keyArg.value);
                        resultEntry.keyParts = keyParts;
                        stack.push(resultEntry);
                        results.add(meta);
                    } else {
                        // Can't resolve key, discard
                        stack.push(new StackEntry(StackEntryType.UNKNOWN, null));
                    }
                    return;
                }

                if (writeInfo != null && argTypes.length >= 2) {
                    // Write operation: pop args, analyze key and value type
                    List<StackEntry> args = popArgs(argTypes.length);
                    stack.poll(); // pop roam objectref

                    StackEntry keyArg = args.get(0);
                    StackEntry valueArg = args.get(1);
                    List<KeyPart> keyParts = toKeyParts(keyArg);
                    if (keyParts != null && !keyParts.isEmpty()) {
                        RoamKeyMeta meta = new RoamKeyMeta();
                        meta.setDirection(writeInfo[0]);
                        meta.setAccessMode(writeInfo[1]);
                        meta.setAccessMethod(writeInfo[2]);
                        meta.setKeyParts(keyParts);
                        results.add(meta);
                    }
                    // put returns the previous value
                    stack.push(new StackEntry(StackEntryType.UNKNOWN, null));
                    return;
                }

                // Other roam methods (getMeta, etc.) - just consume and produce
                popArgs(argTypes.length);
                stack.poll(); // objectref
                if (returnType.getSort() != Type.VOID) {
                    stack.push(new StackEntry(StackEntryType.UNKNOWN, null));
                }
                return;
            }

            // Check for cross-method calls that pass roam as argument
            if (depth < MAX_DEPTH && (opcode == Opcodes.INVOKEVIRTUAL || opcode == Opcodes.INVOKESTATIC || opcode == Opcodes.INVOKESPECIAL)) {
                boolean passesRoam = false;
                for (Type argType : argTypes) {
                    if (argType.getInternalName().equals(ROAM_INTERNAL_NAME)) {
                        passesRoam = true;
                        break;
                    }
                }
                if (passesRoam) {
                    try {
                        boolean isStaticCall = (opcode == Opcodes.INVOKESTATIC);
                        Class<?> targetClass = owner.equals(ownerInternalName)
                                ? leafClass
                                : Class.forName(owner.replace('/', '.'), false, leafClass.getClassLoader());
                        List<RoamKeyMeta> subResults = scanMethod(targetClass, owner, name, descriptor, isStaticCall, visited, depth + 1);
                        results.addAll(subResults);
                    } catch (Exception e) {
                        log.debug("failed to scan cross-method {}.{}: {}", owner, name, e.getMessage());
                    }
                }
            }

            // Regular method call - consume args and objectref, push result
            popArgs(argTypes.length);
            if (opcode != Opcodes.INVOKESTATIC) {
                stack.poll(); // objectref
            }
            if (returnType.getSort() != Type.VOID) {
                stack.push(new StackEntry(StackEntryType.UNKNOWN, null));
            }
        }

        @Override
        public void visitJumpInsn(int opcode, Label label) {
            // Conditional jumps consume stack entries
            switch (opcode) {
                case Opcodes.IFEQ: case Opcodes.IFNE: case Opcodes.IFLT:
                case Opcodes.IFGE: case Opcodes.IFGT: case Opcodes.IFLE:
                case Opcodes.IFNULL: case Opcodes.IFNONNULL:
                    stack.poll();
                    break;
                case Opcodes.IF_ICMPEQ: case Opcodes.IF_ICMPNE: case Opcodes.IF_ICMPLT:
                case Opcodes.IF_ICMPGE: case Opcodes.IF_ICMPGT: case Opcodes.IF_ICMPLE:
                case Opcodes.IF_ACMPEQ: case Opcodes.IF_ACMPNE:
                    stack.poll();
                    stack.poll();
                    break;
                default:
                    break;
            }
        }

        @Override
        public void visitMultiANewArrayInsn(String descriptor, int numDimensions) {
            for (int i = 0; i < numDimensions; i++) stack.poll();
            stack.push(new StackEntry(StackEntryType.UNKNOWN, null));
        }

        @Override
        public void visitIincInsn(int varIndex, int increment) {
            // No stack change
        }

        @Override
        public void visitTableSwitchInsn(int min, int max, Label dflt, Label... labels) {
            stack.poll();
        }

        @Override
        public void visitLookupSwitchInsn(Label dflt, int[] keys, Label[] labels) {
            stack.poll();
        }

        private List<StackEntry> popArgs(int count) {
            List<StackEntry> args = new ArrayList<>(count);
            for (int i = count - 1; i >= 0; i--) {
                StackEntry e = stack.poll();
                args.add(0, e != null ? e : new StackEntry(StackEntryType.UNKNOWN, null));
            }
            return args;
        }

        private List<KeyPart> toKeyParts(StackEntry entry) {
            if (entry == null || entry.type == StackEntryType.UNKNOWN) {
                return null;
            }
            List<KeyPart> parts = new ArrayList<>();
            KeyPart kp = new KeyPart();
            switch (entry.type) {
                case LITERAL:
                    kp.setType("literal");
                    kp.setValue(entry.value);
                    parts.add(kp);
                    break;
                case FIELD:
                    kp.setType("field");
                    kp.setRef(entry.value);
                    parts.add(kp);
                    break;
                case ROAM_DERIVED:
                    kp.setType("roamDerived");
                    kp.setFromKey(entry.value);
                    parts.add(kp);
                    break;
                case COMPOSITE:
                    if (entry.compositeParts != null && !entry.compositeParts.isEmpty()) {
                        kp.setType("composite");
                        kp.setParts(entry.compositeParts);
                        parts.add(kp);
                    } else {
                        return null;
                    }
                    break;
                default:
                    return null;
            }
            return parts;
        }

        private StackEntry buildCompositeFromRecipe(String recipe, List<StackEntry> args) {
            // recipe like "\u0001_\u0001" where \u0001 are arg placeholders
            List<KeyPart> parts = new ArrayList<>();
            int argIdx = 0;
            StringBuilder literalBuf = new StringBuilder();

            for (int i = 0; i < recipe.length(); i++) {
                char c = recipe.charAt(i);
                if (c == '\u0001' || c == '\u0002') {
                    if (literalBuf.length() > 0) {
                        KeyPart lit = new KeyPart();
                        lit.setType("literal");
                        lit.setValue(literalBuf.toString());
                        parts.add(lit);
                        literalBuf.setLength(0);
                    }
                    if (argIdx < args.size()) {
                        StackEntry arg = args.get(argIdx);
                        List<KeyPart> argParts = toKeyParts(arg);
                        if (argParts != null) {
                            parts.addAll(argParts);
                        } else {
                            // Can't resolve this part - whole composite is unresolvable
                            return new StackEntry(StackEntryType.UNKNOWN, null);
                        }
                    }
                    argIdx++;
                } else {
                    literalBuf.append(c);
                }
            }
            if (literalBuf.length() > 0) {
                KeyPart lit = new KeyPart();
                lit.setType("literal");
                lit.setValue(literalBuf.toString());
                parts.add(lit);
            }

            if (parts.isEmpty()) {
                return new StackEntry(StackEntryType.UNKNOWN, null);
            }
            if (parts.size() == 1) {
                StackEntry single = new StackEntry(parts.get(0));
                return single;
            }
            StackEntry composite = new StackEntry(StackEntryType.COMPOSITE, null);
            composite.compositeParts = parts;
            return composite;
        }

    }

    /**
     * Scan a specific method within a class for roam key accesses.
     */
    private static List<RoamKeyMeta> scanMethod(Class<?> clazz, String classInternalName,
                                                  String methodName, String methodDesc,
                                                  boolean isStatic,
                                                  Set<String> visited, int depth) throws IOException {
        String visitKey = classInternalName + "." + methodName + methodDesc;
        if (!visited.add(visitKey) || depth > MAX_DEPTH) {
            return Collections.emptyList();
        }

        ClassReader cr = readClass(clazz);
        if (cr == null) {
            return Collections.emptyList();
        }

        // Determine which slot holds the IceRoam parameter
        // For instance methods: slot 0 = this, then params start at slot 1
        // For static methods: params start at slot 0
        int roamSlot = findRoamSlot(methodDesc, isStatic);
        if (roamSlot < 0) {
            return Collections.emptyList();
        }

        List<RoamKeyMeta> results = new ArrayList<>();
        Map<String, String> classFields = new HashMap<>();

        cr.accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
                if ((access & Opcodes.ACC_STATIC) == 0 && (access & Opcodes.ACC_FINAL) == 0) {
                    classFields.put(name, descriptor);
                }
                return null;
            }

            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                if (name.equals(methodName) && descriptor.equals(methodDesc)) {
                    return new RoamMethodVisitor(Opcodes.ASM9, results, classInternalName, classFields, clazz, visited, depth, roamSlot);
                }
                return null;
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);

        return results;
    }

    /**
     * Find the local variable slot of the IceRoam parameter in a method descriptor.
     * Returns -1 if no IceRoam parameter found.
     */
    private static int findRoamSlot(String methodDesc, boolean isStatic) {
        Type[] argTypes = Type.getArgumentTypes(methodDesc);
        int slot = isStatic ? 0 : 1; // slot 0 = this for instance methods
        for (Type argType : argTypes) {
            if (argType.getInternalName().equals(ROAM_INTERNAL_NAME)) {
                return slot;
            }
            slot += argType.getSize(); // long/double take 2 slots
        }
        return -1;
    }

    private enum StackEntryType {
        LITERAL, FIELD, ROAM_DERIVED, COMPOSITE, ROAM_REF, UNKNOWN
    }

    private static class StackEntry {
        final StackEntryType type;
        final String value;
        List<KeyPart> keyParts;
        List<KeyPart> compositeParts;

        StackEntry(StackEntryType type, String value) {
            this.type = type;
            this.value = value;
        }

        StackEntry(KeyPart kp) {
            switch (kp.getType()) {
                case "literal":
                    this.type = StackEntryType.LITERAL;
                    this.value = kp.getValue();
                    break;
                case "field":
                    this.type = StackEntryType.FIELD;
                    this.value = kp.getRef();
                    break;
                case "roamDerived":
                    this.type = StackEntryType.ROAM_DERIVED;
                    this.value = kp.getFromKey();
                    break;
                default:
                    this.type = StackEntryType.UNKNOWN;
                    this.value = null;
            }
        }
    }
}
