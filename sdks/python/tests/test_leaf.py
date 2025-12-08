"""Tests for leaf node registration and execution."""

import unittest
from ice.context.roam import Roam
from ice.context.pack import Pack
from ice.context.context import Context
from ice.enums import RunState, NodeType
from ice.leaf.registry import (
    leaf, register_leaf, is_registered, get_leaf_nodes, create_leaf_node
)


# Test leaf node classes
@leaf("com.test.ScoreFlow")
class ScoreFlow:
    """Test flow leaf node."""
    threshold: int = 0
    
    def do_roam_flow(self, roam: Roam) -> bool:
        return roam.get_int("score", 0) >= self.threshold


@leaf("com.test.AmountResult", name="Amount", desc="Calculate amount")
class AmountResult:
    """Test result leaf node."""
    multiplier: float = 1.0
    
    def do_roam_result(self, roam: Roam) -> bool:
        score = roam.get_int("score", 0)
        roam.put("amount", score * self.multiplier)
        return True


@leaf("com.test.LogNone")
class LogNone:
    """Test none leaf node."""
    message: str = ""
    
    def do_roam_none(self, roam: Roam) -> None:
        roam.put("logged", self.message)


class TestLeafRegistry(unittest.TestCase):
    """Tests for leaf registration."""
    
    def test_decorator_registration(self):
        """Test @leaf decorator registers classes."""
        self.assertTrue(is_registered("com.test.ScoreFlow"))
        self.assertTrue(is_registered("com.test.AmountResult"))
        self.assertTrue(is_registered("com.test.LogNone"))
    
    def test_get_leaf_nodes(self):
        """Test getting registered leaf nodes info."""
        nodes = get_leaf_nodes()
        
        # Find our test nodes
        class_names = [n.clazz for n in nodes]
        self.assertIn("com.test.ScoreFlow", class_names)
        self.assertIn("com.test.AmountResult", class_names)
        
        # Check metadata
        amount_node = next(n for n in nodes if n.clazz == "com.test.AmountResult")
        self.assertEqual(amount_node.name, "Amount")
        self.assertEqual(amount_node.desc, "Calculate amount")


class TestLeafExecution(unittest.TestCase):
    """Tests for leaf node execution."""
    
    def test_flow_leaf_true(self):
        """Test flow leaf returning true."""
        node = create_leaf_node("com.test.ScoreFlow", '{"threshold": 60}')
        
        pack = Pack()
        pack.roam.put("score", 80)
        ctx = Context(1, pack)
        
        result = node.process(ctx)
        self.assertEqual(result, RunState.TRUE)
    
    def test_flow_leaf_false(self):
        """Test flow leaf returning false."""
        node = create_leaf_node("com.test.ScoreFlow", '{"threshold": 60}')
        
        pack = Pack()
        pack.roam.put("score", 50)
        ctx = Context(1, pack)
        
        result = node.process(ctx)
        self.assertEqual(result, RunState.FALSE)
    
    def test_result_leaf(self):
        """Test result leaf execution."""
        node = create_leaf_node("com.test.AmountResult", '{"multiplier": 2.0}')
        
        pack = Pack()
        pack.roam.put("score", 100)
        ctx = Context(1, pack)
        
        result = node.process(ctx)
        self.assertEqual(result, RunState.TRUE)
        self.assertEqual(pack.roam.get("amount"), 200.0)
    
    def test_none_leaf(self):
        """Test none leaf execution."""
        node = create_leaf_node("com.test.LogNone", '{"message": "hello"}')
        
        pack = Pack()
        ctx = Context(1, pack)
        
        result = node.process(ctx)
        self.assertEqual(result, RunState.NONE)
        self.assertEqual(pack.roam.get("logged"), "hello")


class TestManualRegistration(unittest.TestCase):
    """Tests for manual registration."""
    
    def test_register_leaf(self):
        """Test manual leaf registration."""
        class CustomLeaf:
            value: int = 0
            
            def do_roam_flow(self, roam: Roam) -> bool:
                return roam.get_int("x", 0) > self.value
        
        register_leaf("com.test.CustomLeaf", CustomLeaf)
        
        self.assertTrue(is_registered("com.test.CustomLeaf"))
        
        node = create_leaf_node("com.test.CustomLeaf", '{"value": 10}')
        pack = Pack()
        pack.roam.put("x", 20)
        ctx = Context(1, pack)
        
        result = node.process(ctx)
        self.assertEqual(result, RunState.TRUE)


if __name__ == "__main__":
    unittest.main()

