package com.ice.core.scan;

import com.ice.common.model.LeafNodeInfo.RoamKeyMeta;
import com.ice.core.context.IceRoam;
import com.ice.core.leaf.base.BaseLeafFlow;
import com.ice.core.leaf.base.BaseLeafNone;
import com.ice.core.leaf.base.BaseLeafResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for RoamKeyScanner - cross-method tracking, depth limits, cycle protection.
 *
 * @author waitmoon
 */
class RoamKeyScannerTest {

    // --- Test leaf classes ---

    static class SimpleReadFlow extends BaseLeafFlow {
        @Override
        protected boolean doFlow(IceRoam roam) {
            return roam.get("score") != null;
        }
    }

    static class SimpleWriteResult extends BaseLeafResult {
        @Override
        protected boolean doResult(IceRoam roam) {
            roam.put("RESULT", 1);
            return true;
        }
    }

    static class FieldKeyFlow extends BaseLeafFlow {
        private String key;

        @Override
        protected boolean doFlow(IceRoam roam) {
            return roam.getDeep(key) != null;
        }
    }

    static class ReadWriteResult extends BaseLeafResult {
        @Override
        protected boolean doResult(IceRoam roam) {
            Object val = roam.get("data");
            roam.put("data", val);
            return true;
        }
    }

    static class CrossMethodFlow extends BaseLeafFlow {
        @Override
        protected boolean doFlow(IceRoam roam) {
            return helper(roam);
        }

        private boolean helper(IceRoam roam) {
            return roam.get("cross_key") != null;
        }
    }

    static class NoRoamAccessNone extends BaseLeafNone {
        @Override
        protected void doNone(IceRoam roam) {
            int x = 1 + 2;
        }
    }

    // --- Tests ---

    @Test
    void testSimpleRead() {
        List<RoamKeyMeta> keys = RoamKeyScanner.scan(SimpleReadFlow.class);
        assertEquals(1, keys.size());
        assertEquals("read", keys.get(0).getDirection());
        assertEquals("literal", keys.get(0).getKeyParts().get(0).getType());
        assertEquals("score", keys.get(0).getKeyParts().get(0).getValue());
    }

    @Test
    void testSimpleWrite() {
        List<RoamKeyMeta> keys = RoamKeyScanner.scan(SimpleWriteResult.class);
        assertEquals(1, keys.size());
        assertEquals("write", keys.get(0).getDirection());
        assertEquals("RESULT", keys.get(0).getKeyParts().get(0).getValue());
    }

    @Test
    void testFieldKey() {
        List<RoamKeyMeta> keys = RoamKeyScanner.scan(FieldKeyFlow.class);
        assertEquals(1, keys.size());
        assertEquals("field", keys.get(0).getKeyParts().get(0).getType());
        assertEquals("key", keys.get(0).getKeyParts().get(0).getRef());
    }

    @Test
    void testReadWriteMerge() {
        List<RoamKeyMeta> keys = RoamKeyScanner.scan(ReadWriteResult.class);
        assertEquals(1, keys.size());
        assertEquals("read_write", keys.get(0).getDirection());
    }

    @Test
    void testCrossMethod() {
        List<RoamKeyMeta> keys = RoamKeyScanner.scan(CrossMethodFlow.class);
        assertEquals(1, keys.size());
        assertEquals("cross_key", keys.get(0).getKeyParts().get(0).getValue());
    }

    @Test
    void testNoRoamAccess() {
        List<RoamKeyMeta> keys = RoamKeyScanner.scan(NoRoamAccessNone.class);
        assertEquals(0, keys.size());
    }
}
