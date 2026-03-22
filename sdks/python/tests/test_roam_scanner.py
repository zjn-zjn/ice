"""Tests for roam key scanner - cross-method tracking, depth limits, cycle protection."""

import unittest
from ice.scan.roam_scanner import scan_roam_keys
from ice.context.roam import Roam


# --- Test leaf classes ---

class SimpleReadFlow:
    """Reads a literal key."""
    def do_flow(self, roam: Roam) -> bool:
        return roam.get("score") is not None


class SimpleWriteResult:
    """Writes a literal key."""
    key: str = ""

    def do_result(self, roam: Roam) -> bool:
        roam.put("RESULT", 1)
        return True


class FieldKeyFlow:
    """Reads using self.key (field reference)."""
    key: str = ""

    def do_flow(self, roam: Roam) -> bool:
        return roam.get_deep(self.key) is not None


class ReadWriteResult:
    """Both reads and writes the same key -> should merge to read_write."""
    key: str = ""

    def do_result(self, roam: Roam) -> bool:
        val = roam.get("data")
        roam.put("data", val)
        return True


class CrossMethodFlow:
    """Passes roam to self._helper, which reads a key."""
    def do_flow(self, roam: Roam) -> bool:
        return self._helper(roam)

    def _helper(self, r: Roam) -> bool:
        return r.get("cross_key") is not None


def _pkg_helper(roam: Roam) -> None:
    """Package-level function that writes a key."""
    roam.put("pkg_written", True)


class PkgFuncNone:
    """Passes roam to a package-level function."""
    def do_none(self, roam: Roam) -> None:
        _pkg_helper(roam)


class CyclicA:
    """Calls self._b which calls self._a -> cycle protection."""
    def do_flow(self, roam: Roam) -> bool:
        return self._b(roam)

    def _b(self, r: Roam) -> bool:
        val = r.get("cyclic_key")
        return self._a(r)

    def _a(self, r: Roam) -> bool:
        return self._b(r)  # cycle!


class NoRoamAccess:
    """No roam key access."""
    def do_none(self, roam: Roam) -> None:
        x = 1 + 2


# --- Tests ---

class TestRoamScanner(unittest.TestCase):

    def test_simple_read(self):
        keys = scan_roam_keys(SimpleReadFlow)
        self.assertEqual(len(keys), 1)
        self.assertEqual(keys[0].direction, "read")
        self.assertEqual(keys[0].keyParts[0].type, "literal")
        self.assertEqual(keys[0].keyParts[0].value, "score")

    def test_simple_write(self):
        keys = scan_roam_keys(SimpleWriteResult)
        self.assertEqual(len(keys), 1)
        self.assertEqual(keys[0].direction, "write")
        self.assertEqual(keys[0].keyParts[0].value, "RESULT")

    def test_field_key(self):
        keys = scan_roam_keys(FieldKeyFlow)
        self.assertEqual(len(keys), 1)
        self.assertEqual(keys[0].keyParts[0].type, "field")
        self.assertEqual(keys[0].keyParts[0].ref, "key")

    def test_read_write_merge(self):
        keys = scan_roam_keys(ReadWriteResult)
        self.assertEqual(len(keys), 1)
        self.assertEqual(keys[0].direction, "read_write")

    def test_cross_method(self):
        keys = scan_roam_keys(CrossMethodFlow)
        self.assertEqual(len(keys), 1)
        self.assertEqual(keys[0].keyParts[0].value, "cross_key")

    def test_pkg_function(self):
        keys = scan_roam_keys(PkgFuncNone)
        self.assertEqual(len(keys), 1)
        self.assertEqual(keys[0].direction, "write")
        self.assertEqual(keys[0].keyParts[0].value, "pkg_written")

    def test_cyclic_no_crash(self):
        """Cyclic cross-method calls should not cause infinite recursion."""
        keys = scan_roam_keys(CyclicA)
        # Should find "cyclic_key" and not crash
        found = any(
            kp.value == "cyclic_key"
            for k in keys
            for kp in k.keyParts
        )
        self.assertTrue(found)

    def test_no_roam_access(self):
        keys = scan_roam_keys(NoRoamAccess)
        self.assertEqual(len(keys), 0)


if __name__ == "__main__":
    unittest.main()
