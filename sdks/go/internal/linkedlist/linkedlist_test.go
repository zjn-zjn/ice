package linkedlist

import (
	"testing"
)

type testItem struct {
	id int
}

func equals(a, b *testItem) bool {
	if a == nil || b == nil {
		return a == b
	}
	return a.id == b.id
}

func TestLinkedList_Empty(t *testing.T) {
	ll := New[*testItem]()

	if ll.Size() != 0 {
		t.Errorf("expected size 0, got %d", ll.Size())
	}
	if !ll.IsEmpty() {
		t.Error("expected IsEmpty to be true")
	}
	if ll.First() != nil {
		t.Error("expected First to be nil")
	}
	if ll.Last() != nil {
		t.Error("expected Last to be nil")
	}
}

func TestLinkedList_Add(t *testing.T) {
	ll := New[*testItem]()

	ll.Add(&testItem{1})
	ll.Add(&testItem{2})
	ll.Add(&testItem{3})

	if ll.Size() != 3 {
		t.Errorf("expected size 3, got %d", ll.Size())
	}
	if ll.IsEmpty() {
		t.Error("expected IsEmpty to be false")
	}
	if ll.First().Item.id != 1 {
		t.Error("expected first item id=1")
	}
	if ll.Last().Item.id != 3 {
		t.Error("expected last item id=3")
	}
}

func TestLinkedList_Remove(t *testing.T) {
	ll := New[*testItem]()

	item1 := &testItem{1}
	item2 := &testItem{2}
	item3 := &testItem{3}

	ll.Add(item1)
	ll.Add(item2)
	ll.Add(item3)

	// Remove middle
	ll.Remove(item2, equals)
	if ll.Size() != 2 {
		t.Errorf("expected size 2, got %d", ll.Size())
	}

	// Verify remaining
	items := ll.ToSlice()
	if items[0].id != 1 || items[1].id != 3 {
		t.Error("remaining items should be 1 and 3")
	}
}

func TestLinkedList_RemoveFirst(t *testing.T) {
	ll := New[*testItem]()

	item1 := &testItem{1}
	item2 := &testItem{2}

	ll.Add(item1)
	ll.Add(item2)

	ll.Remove(item1, equals)

	if ll.Size() != 1 {
		t.Errorf("expected size 1, got %d", ll.Size())
	}
	if ll.First().Item.id != 2 {
		t.Error("expected first item id=2")
	}
}

func TestLinkedList_RemoveLast(t *testing.T) {
	ll := New[*testItem]()

	item1 := &testItem{1}
	item2 := &testItem{2}

	ll.Add(item1)
	ll.Add(item2)

	ll.Remove(item2, equals)

	if ll.Size() != 1 {
		t.Errorf("expected size 1, got %d", ll.Size())
	}
	if ll.Last().Item.id != 1 {
		t.Error("expected last item id=1")
	}
}

func TestLinkedList_Clear(t *testing.T) {
	ll := New[*testItem]()

	ll.Add(&testItem{1})
	ll.Add(&testItem{2})
	ll.Clear()

	if !ll.IsEmpty() {
		t.Error("expected list to be empty after clear")
	}
	if ll.Size() != 0 {
		t.Errorf("expected size 0, got %d", ll.Size())
	}
}

func TestLinkedList_Get(t *testing.T) {
	ll := New[*testItem]()

	ll.Add(&testItem{1})
	ll.Add(&testItem{2})
	ll.Add(&testItem{3})

	if ll.Get(0).id != 1 {
		t.Error("expected Get(0).id=1")
	}
	if ll.Get(1).id != 2 {
		t.Error("expected Get(1).id=2")
	}
	if ll.Get(2).id != 3 {
		t.Error("expected Get(2).id=3")
	}
}

func TestLinkedList_ToSlice(t *testing.T) {
	ll := New[*testItem]()

	ll.Add(&testItem{1})
	ll.Add(&testItem{2})
	ll.Add(&testItem{3})

	slice := ll.ToSlice()
	if len(slice) != 3 {
		t.Errorf("expected length 3, got %d", len(slice))
	}
	if slice[0].id != 1 || slice[1].id != 2 || slice[2].id != 3 {
		t.Error("slice order mismatch")
	}
}

func TestLinkedList_AddAll(t *testing.T) {
	ll := New[*testItem]()

	ll.AddAll(&testItem{1}, &testItem{2}, &testItem{3})

	if ll.Size() != 3 {
		t.Errorf("expected size 3, got %d", ll.Size())
	}
}

func TestLinkedList_RemoveNonExistent(t *testing.T) {
	ll := New[*testItem]()

	ll.Add(&testItem{1})
	removed := ll.Remove(&testItem{2}, equals) // Non-existent

	if removed {
		t.Error("should return false for non-existent item")
	}
	if ll.Size() != 1 {
		t.Error("size should still be 1")
	}
}

func TestLinkedList_Iteration(t *testing.T) {
	ll := New[*testItem]()

	ll.Add(&testItem{1})
	ll.Add(&testItem{2})
	ll.Add(&testItem{3})

	// Iterate using Next
	sum := 0
	for node := ll.First(); node != nil; node = node.Next {
		sum += node.Item.id
	}
	if sum != 6 {
		t.Errorf("expected sum 6, got %d", sum)
	}
}

func TestLinkedList_IterationAfterRemove(t *testing.T) {
	ll := New[*testItem]()

	item1 := &testItem{1}
	item2 := &testItem{2}
	item3 := &testItem{3}

	ll.Add(item1)
	ll.Add(item2)
	ll.Add(item3)

	// Remove middle item
	ll.Remove(item2, equals)

	// Iteration should still work (this is the key feature)
	ids := make([]int, 0)
	for node := ll.First(); node != nil; node = node.Next {
		ids = append(ids, node.Item.id)
	}

	if len(ids) != 2 {
		t.Errorf("expected 2 items, got %d", len(ids))
	}
	if ids[0] != 1 || ids[1] != 3 {
		t.Errorf("expected [1, 3], got %v", ids)
	}
}
