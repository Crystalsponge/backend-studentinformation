package crys.sims.datastructure;

/**
 * FIFO queue built from scratch on a circular array buffer (no java.util
 * imports — course requirement: data structures implemented via array
 * manipulation). Grows by doubling when full, preserving order.
 * Null elements are rejected.
 *
 * removeFirstMatch additionally supports waitlist semantics: removal is
 * always the earliest-arrived match, so per-key FIFO order is preserved.
 */
public class ArrayQueue<T> {

    /** Predicate used by removeFirstMatch; local interface to avoid java.util imports. */
    public interface Matcher<T> {
        boolean matches(T item);
    }

    private static final int DEFAULT_CAPACITY = 8;

    private T[] elements;
    private int head;
    private int tail;
    private int size;

    @SuppressWarnings("unchecked")
    public ArrayQueue() {
        this.elements = (T[]) new Object[DEFAULT_CAPACITY];
        this.head = 0;
        this.tail = 0;
        this.size = 0;
    }

    @SuppressWarnings("unchecked")
    public ArrayQueue(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("Capacity must be positive: " + capacity);
        }
        this.elements = (T[]) new Object[capacity];
        this.head = 0;
        this.tail = 0;
        this.size = 0;
    }

    public void enqueue(T item) {
        if (item == null) {
            throw new IllegalArgumentException("Null elements are not allowed");
        }
        if (size == elements.length) {
            grow();
        }
        elements[tail] = item;
        tail = (tail + 1) % elements.length;
        size++;
    }

    public T dequeue() {
        if (size == 0) {
            throw new IllegalStateException("Queue is empty");
        }
        T item = elements[head];
        elements[head] = null;
        head = (head + 1) % elements.length;
        size--;
        return item;
    }

    public T peek() {
        if (size == 0) {
            throw new IllegalStateException("Queue is empty");
        }
        return elements[head];
    }

    public boolean isEmpty() {
        return size == 0;
    }

    public int size() {
        return size;
    }

    public T get(int index) {
        if (index < 0 || index >= size) {
            throw new IndexOutOfBoundsException("Index: " + index + ", size: " + size);
        }
        return elements[(head + index) % elements.length];
    }

    public boolean removeFirstMatch(Matcher<T> matcher) {
        if (matcher == null) {
            throw new IllegalArgumentException("Matcher must not be null");
        }
        for (int i = 0; i < size; i++) {
            if (matcher.matches(elementAt(i))) {
                for (int j = i; j < size - 1; j++) {
                    elements[(head + j) % elements.length] = elements[(head + j + 1) % elements.length];
                }
                elements[(head + size - 1) % elements.length] = null;
                size--;
                return true;
            }
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    public T[] toArray() {
        Object[] copy = new Object[size];
        for (int i = 0; i < size; i++) {
            copy[i] = elementAt(i);
        }
        return (T[]) copy;
    }

    public void clear() {
        for (int i = 0; i < size; i++) {
            elements[(head + i) % elements.length] = null;
        }
        head = 0;
        tail = 0;
        size = 0;
    }

    private T elementAt(int index) {
        return elements[(head + index) % elements.length];
    }

    @SuppressWarnings("unchecked")
    private void grow() {
        int newCapacity = elements.length * 2;
        Object[] grown = new Object[newCapacity];
        for (int i = 0; i < size; i++) {
            grown[i] = elementAt(i);
        }
        elements = (T[]) grown;
        head = 0;
        tail = size;
    }
}
