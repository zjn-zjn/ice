"""Tests for relation nodes."""

import unittest
from ice.context.roam import Roam
from ice.enums import RunState
from ice.relation.serial import And, Any, All, TrueNode, NoneNode
from ice.node.base import Node, Base, process_with_base


class MockNode(Base, Node):
    """Mock node for testing."""

    def __init__(self, return_state: RunState):
        super().__init__()
        self._return_state = return_state
        self.ice_log_name = "Mock"

    def process(self, roam: Roam) -> RunState:
        return process_with_base(self, roam, self._do_process)

    def _do_process(self, roam: Roam) -> RunState:
        return self._return_state


class TestAnd(unittest.TestCase):
    """Tests for And relation node."""

    def test_empty_children(self):
        """Empty And returns NONE."""
        and_node = And()
        roam = Roam.create()

        result = and_node.process(roam)
        self.assertEqual(result, RunState.NONE)

    def test_all_true(self):
        """All TRUE returns TRUE."""
        and_node = And()
        and_node.children = [
            MockNode(RunState.TRUE),
            MockNode(RunState.TRUE),
        ]
        roam = Roam.create()

        result = and_node.process(roam)
        self.assertEqual(result, RunState.TRUE)

    def test_has_false(self):
        """Has FALSE returns FALSE."""
        and_node = And()
        and_node.children = [
            MockNode(RunState.TRUE),
            MockNode(RunState.FALSE),
            MockNode(RunState.TRUE),
        ]
        roam = Roam.create()

        result = and_node.process(roam)
        self.assertEqual(result, RunState.FALSE)

    def test_all_none(self):
        """All NONE returns NONE."""
        and_node = And()
        and_node.children = [
            MockNode(RunState.NONE),
            MockNode(RunState.NONE),
        ]
        roam = Roam.create()

        result = and_node.process(roam)
        self.assertEqual(result, RunState.NONE)


class TestAny(unittest.TestCase):
    """Tests for Any relation node."""

    def test_empty_children(self):
        """Empty Any returns NONE."""
        any_node = Any()
        roam = Roam.create()

        result = any_node.process(roam)
        self.assertEqual(result, RunState.NONE)

    def test_has_true(self):
        """Has TRUE returns TRUE."""
        any_node = Any()
        any_node.children = [
            MockNode(RunState.FALSE),
            MockNode(RunState.TRUE),
        ]
        roam = Roam.create()

        result = any_node.process(roam)
        self.assertEqual(result, RunState.TRUE)

    def test_all_false(self):
        """All FALSE returns FALSE."""
        any_node = Any()
        any_node.children = [
            MockNode(RunState.FALSE),
            MockNode(RunState.FALSE),
        ]
        roam = Roam.create()

        result = any_node.process(roam)
        self.assertEqual(result, RunState.FALSE)


class TestAll(unittest.TestCase):
    """Tests for All relation node."""

    def test_executes_all(self):
        """All executes all children."""
        all_node = All()
        nodes = [MockNode(RunState.TRUE) for _ in range(3)]
        all_node.children = nodes
        roam = Roam.create()

        result = all_node.process(roam)
        self.assertEqual(result, RunState.TRUE)

    def test_has_false(self):
        """Has FALSE returns FALSE."""
        all_node = All()
        all_node.children = [
            MockNode(RunState.TRUE),
            MockNode(RunState.FALSE),
            MockNode(RunState.TRUE),
        ]
        roam = Roam.create()

        result = all_node.process(roam)
        self.assertEqual(result, RunState.FALSE)


class TestTrueNode(unittest.TestCase):
    """Tests for True relation node."""

    def test_always_true(self):
        """TrueNode always returns TRUE."""
        true_node = TrueNode()
        true_node.children = [
            MockNode(RunState.FALSE),
            MockNode(RunState.NONE),
        ]
        roam = Roam.create()

        result = true_node.process(roam)
        self.assertEqual(result, RunState.TRUE)


class TestNoneNode(unittest.TestCase):
    """Tests for None relation node."""

    def test_always_none(self):
        """NoneNode always returns NONE."""
        none_node = NoneNode()
        none_node.children = [
            MockNode(RunState.TRUE),
            MockNode(RunState.FALSE),
        ]
        roam = Roam.create()

        result = none_node.process(roam)
        self.assertEqual(result, RunState.NONE)


if __name__ == "__main__":
    unittest.main()
