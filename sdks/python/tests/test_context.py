"""Tests for context module."""

import unittest
from ice.context.roam import Roam


class TestRoam(unittest.TestCase):
    """Tests for Roam class."""

    def test_put_get(self):
        """Test basic put and get operations."""
        roam = Roam()
        roam.put("key1", "value1")
        roam.put("key2", 123)

        self.assertEqual(roam.get("key1"), "value1")
        self.assertEqual(roam.get("key2"), 123)
        self.assertIsNone(roam.get("nonexistent"))
        self.assertEqual(roam.get("nonexistent", "default"), "default")

    def test_put_all(self):
        """Test put_all operation (batch put)."""
        roam = Roam()
        roam.put_all({"a": 1, "b": 2, "c": 3})

        self.assertEqual(roam.get("a"), 1)
        self.assertEqual(roam.get("b"), 2)
        self.assertEqual(roam.get("c"), 3)

    def test_put_deep(self):
        """Test put_deep operation (deep key)."""
        roam = Roam()
        roam.put_deep("user.profile.age", 25)
        roam.put_deep("user.profile.name", "test")

        self.assertEqual(roam.get_deep("user.profile.age"), 25)
        self.assertEqual(roam.get_deep("user.profile.name"), "test")

        user = roam.get("user")
        self.assertIsInstance(user, dict)
        self.assertEqual(user["profile"]["age"], 25)

    def test_get_deep(self):
        """Test deep key access."""
        roam = Roam()
        roam.put("user", {"name": "test", "profile": {"age": 25}})

        self.assertEqual(roam.get_deep("user.name"), "test")
        self.assertEqual(roam.get_deep("user.profile.age"), 25)
        self.assertIsNone(roam.get_deep("user.nonexistent"))

    def test_resolve(self):
        """Test reference resolution."""
        roam = Roam()
        roam.put("score", 100)
        roam.put("ref", "@score")

        ref_value = roam.get("ref")
        self.assertEqual(roam.resolve(ref_value), 100)
        self.assertEqual(roam.resolve("not_ref"), "not_ref")

    def test_type_values(self):
        """Test storing and retrieving typed values."""
        roam = Roam()
        roam.put("str", "hello")
        roam.put("int", 42)
        roam.put("float", 3.14)
        roam.put("bool", True)
        roam.put("list", [1, 2, 3])
        roam.put("dict", {"a": 1})

        self.assertEqual(roam.get("str"), "hello")
        self.assertEqual(roam.get("int"), 42)
        self.assertAlmostEqual(roam.get("float"), 3.14)
        self.assertTrue(roam.get("bool"))
        self.assertEqual(roam.get("list"), [1, 2, 3])
        self.assertEqual(roam.get("dict"), {"a": 1})

    def test_shallow_copy(self):
        """Test shallow copy for parallel execution."""
        roam = Roam()
        roam.put("key", "value")

        copy = roam.clone()
        copy.put("key", "modified")
        copy.put("new_key", "new_value")

        # Original should be unchanged
        self.assertEqual(roam.get("key"), "value")
        self.assertIsNone(roam.get("new_key"))

        # Copy should have changes
        self.assertEqual(copy.get("key"), "modified")
        self.assertEqual(copy.get("new_key"), "new_value")

    def test_str_json(self):
        """Test JSON string representation."""
        roam = Roam()
        roam.put("name", "test")
        roam.put("value", 123)

        s = str(roam)
        self.assertIn("name", s)
        self.assertIn("test", s)

    def test_meta_is_dict(self):
        """Test that _ice is a plain dict."""
        roam = Roam.create()
        ice = roam.get_meta()
        self.assertIsNotNone(ice)
        self.assertIsInstance(ice, dict)


class TestIceMeta(unittest.TestCase):
    """Tests for _ice metadata via Roam."""

    def test_create_with_defaults(self):
        """Test Roam.create() with default _ice metadata."""
        roam = Roam.create()

        self.assertEqual(roam.get_id(), 0)
        self.assertEqual(roam.get_scene(), "")
        self.assertGreater(roam.get_ts(), 0)
        self.assertNotEqual(roam.get_trace(), "")

    def test_meta_access(self):
        """Test _ice access via convenience methods."""
        roam = Roam.create()
        roam.set_id(42)
        roam.set_scene("checkout")

        self.assertEqual(roam.get_id(), 42)
        self.assertEqual(roam.get_scene(), "checkout")

    def test_process_info(self):
        """Test process info collection."""
        roam = Roam.create()

        roam.get_process().write("[test]")
        self.assertEqual(roam.get_process_info(), "[test]")

    def test_clone(self):
        """Test roam clone with independent process."""
        roam = Roam.create()
        roam.put("key", "value")
        roam.set_id(1)
        roam.get_process().write("[original]")

        cloned = roam.clone()
        cloned.put("key", "modified")
        cloned.set_id(2)
        cloned.get_process().write("[cloned]")

        # Original unchanged
        self.assertEqual(roam.get("key"), "value")
        self.assertEqual(roam.get_id(), 1)
        self.assertEqual(roam.get_process_info(), "[original]")

        # Clone has its own state
        self.assertEqual(cloned.get("key"), "modified")
        self.assertEqual(cloned.get_id(), 2)
        self.assertEqual(cloned.get_process_info(), "[cloned]")


if __name__ == "__main__":
    unittest.main()
