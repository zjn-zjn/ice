// Package linkedlist provides a linked list implementation safe for hot updates.
// Unlike standard linked lists, this implementation does not break the next link
// when removing nodes, ensuring safe iteration during concurrent updates.
package linkedlist

// Node represents a node in the linked list.
type Node[T any] struct {
	Item T
	Next *Node[T]
	Prev *Node[T]
}

// LinkedList is a doubly linked list that is safe for iteration during updates.
type LinkedList[T any] struct {
	first *Node[T]
	last  *Node[T]
	size  int
}

// New creates a new LinkedList.
func New[T any]() *LinkedList[T] {
	return &LinkedList[T]{}
}

// IsEmpty returns true if the list is empty.
func (l *LinkedList[T]) IsEmpty() bool {
	return l.size == 0
}

// Size returns the number of elements in the list.
func (l *LinkedList[T]) Size() int {
	return l.size
}

// First returns the first node in the list.
func (l *LinkedList[T]) First() *Node[T] {
	return l.first
}

// Last returns the last node in the list.
func (l *LinkedList[T]) Last() *Node[T] {
	return l.last
}

// Add appends an element to the end of the list.
func (l *LinkedList[T]) Add(item T) {
	la := l.last
	newNode := &Node[T]{Item: item, Prev: la}
	l.last = newNode
	if la == nil {
		l.first = newNode
	} else {
		la.Next = newNode
	}
	l.size++
}

// AddAll appends multiple elements to the end of the list.
func (l *LinkedList[T]) AddAll(items ...T) {
	for _, item := range items {
		l.Add(item)
	}
}

// Get returns the element at the specified index.
func (l *LinkedList[T]) Get(index int) T {
	return l.node(index).Item
}

func (l *LinkedList[T]) node(index int) *Node[T] {
	if index < 0 || index >= l.size {
		var zero T
		return &Node[T]{Item: zero}
	}
	if index < l.size/2 {
		x := l.first
		for i := 0; i < index; i++ {
			x = x.Next
		}
		return x
	}
	x := l.last
	for i := l.size - 1; i > index; i-- {
		x = x.Prev
	}
	return x
}

// Remove removes the first occurrence of the specified item.
// Note: This does NOT break the Next link to ensure safe iteration.
func (l *LinkedList[T]) Remove(item T, equals func(a, b T) bool) bool {
	for x := l.first; x != nil; x = x.Next {
		if equals(x.Item, item) {
			l.unlink(x)
			return true
		}
	}
	return false
}

func (l *LinkedList[T]) unlink(x *Node[T]) {
	next := x.Next
	prev := x.Prev

	if prev == nil {
		l.first = next
	} else {
		prev.Next = next
		// Note: we do NOT set x.Prev = nil to maintain iteration safety
	}

	if next == nil {
		l.last = prev
	} else {
		next.Prev = prev
		// Note: we do NOT set x.Next = nil to maintain iteration safety
	}

	l.size--
}

// Clear removes all elements from the list.
func (l *LinkedList[T]) Clear() {
	l.first = nil
	l.last = nil
	l.size = 0
}

// ToSlice converts the list to a slice.
func (l *LinkedList[T]) ToSlice() []T {
	result := make([]T, 0, l.size)
	for x := l.first; x != nil; x = x.Next {
		result = append(result, x.Item)
	}
	return result
}
