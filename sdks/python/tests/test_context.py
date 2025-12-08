"""Tests for context module."""

import unittest
from ice.context.roam import Roam
from ice.context.pack import Pack
from ice.context.context import Context


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
    
    def test_put_multi(self):
        """Test put_multi operation (multi-level key)."""
        roam = Roam()
        roam.put_multi("user.profile.age", 25)
        roam.put_multi("user.profile.name", "test")
        
        self.assertEqual(roam.get_multi("user.profile.age"), 25)
        self.assertEqual(roam.get_multi("user.profile.name"), "test")
        
        user = roam.get("user")
        self.assertIsInstance(user, dict)
        self.assertEqual(user["profile"]["age"], 25)
    
    def test_get_multi(self):
        """Test multi-level key access."""
        roam = Roam()
        roam.put("user", {"name": "test", "profile": {"age": 25}})
        
        self.assertEqual(roam.get_multi("user.name"), "test")
        self.assertEqual(roam.get_multi("user.profile.age"), 25)
        self.assertIsNone(roam.get_multi("user.nonexistent"))
    
    def test_get_union(self):
        """Test union reference resolution."""
        roam = Roam()
        roam.put("score", 100)
        roam.put("ref", "@score")
        
        ref_value = roam.get("ref")
        self.assertEqual(roam.get_union(ref_value), 100)
        self.assertEqual(roam.get_union("not_ref"), "not_ref")
    
    def test_type_getters(self):
        """Test type-specific getters."""
        roam = Roam()
        roam.put("str", "hello")
        roam.put("int", 42)
        roam.put("float", 3.14)
        roam.put("bool", True)
        roam.put("list", [1, 2, 3])
        roam.put("dict", {"a": 1})
        
        self.assertEqual(roam.get_str("str"), "hello")
        self.assertEqual(roam.get_int("int"), 42)
        self.assertAlmostEqual(roam.get_float("float"), 3.14)
        self.assertTrue(roam.get_bool("bool"))
        self.assertEqual(roam.get_list("list"), [1, 2, 3])
        self.assertEqual(roam.get_dict("dict"), {"a": 1})
    
    def test_shallow_copy(self):
        """Test shallow copy for parallel execution."""
        roam = Roam()
        roam.put("key", "value")
        
        copy = roam.shallow_copy()
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


class TestPack(unittest.TestCase):
    """Tests for Pack class."""
    
    def test_creation(self):
        """Test pack creation with defaults."""
        pack = Pack()
        
        self.assertEqual(pack.ice_id, 0)
        self.assertEqual(pack.scene, "")
        self.assertEqual(pack.conf_id, 0)
        self.assertIsNotNone(pack.roam)
        self.assertGreater(pack.request_time, 0)
        self.assertNotEqual(pack.trace_id, "")
    
    def test_chaining(self):
        """Test method chaining."""
        pack = Pack().set_ice_id(1).set_scene("test").set_debug(1)
        
        self.assertEqual(pack.ice_id, 1)
        self.assertEqual(pack.scene, "test")
        self.assertEqual(pack.debug, 1)
    
    def test_clone(self):
        """Test pack cloning."""
        pack = Pack(ice_id=1)
        pack.roam.put("key", "value")
        
        clone = pack.clone()
        clone.ice_id = 2
        clone.roam.put("key", "modified")
        
        # Original unchanged
        self.assertEqual(pack.ice_id, 1)
        self.assertEqual(pack.roam.get("key"), "value")
    
    def test_str_json(self):
        """Test JSON string representation."""
        pack = Pack(ice_id=1, scene="test")
        
        s = str(pack)
        self.assertIn("iceId", s)
        self.assertIn("scene", s)


class TestContext(unittest.TestCase):
    """Tests for Context class."""
    
    def test_creation(self):
        """Test context creation."""
        pack = Pack(ice_id=1)
        ctx = Context(1, pack)
        
        self.assertEqual(ctx.ice_id, 1)
        self.assertEqual(ctx.pack, pack)
        self.assertGreater(ctx.ice_time, 0)
    
    def test_process_info(self):
        """Test process info collection."""
        pack = Pack()
        ctx = Context(1, pack)
        
        ctx.process_info.write("[test]")
        self.assertEqual(ctx.get_process_info(), "[test]")


if __name__ == "__main__":
    unittest.main()

