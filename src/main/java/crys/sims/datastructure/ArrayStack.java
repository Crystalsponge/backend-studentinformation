package crys.sims.datastructure;

/**
 * LIFO stack built from scratch on a plain array (no java.util imports —
 * course requirement: data structures implemented via array manipulation).
 * Grows by doubling when full. Null elements are rejected.
 */
public class ArrayStack<T> {

    private static final int DEFAULT_CAPACITY = 8;

    private T[] elements;
    private int size;

    @SuppressWarnings("unchecked")
    public ArrayStack() {
        this.elements = (T[]) new Object[DEFAULT_CAPACITY];
        this.size = 0;
    }

    @SuppressWarnings("unchecked")
    public ArrayStack(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("Capacity must be positive: " + capacity);
        }
        this.elements = (T[]) new Object[capacity];
        this.size = 0;
    }

    public void push(T item) {
        if (item == null) {
            throw new IllegalArgumentException("Null elements are not allowed");
        }
        if (size == elements.length) {
            grow();
        }
        elements[size] = item;
        size++;
    }

    public T pop() {
        if (size == 0) {
            throw new IllegalStateException("Stack is empty");
        }
        size--;
        T item = elements[size];
        elements[size] = null;
        return item;
    }

    public T peek() {
        if (size == 0) {
            throw new IllegalStateException("Stack is empty");
        }
        return elements[size - 1];
    }

    public boolean isEmpty() {
        return size == 0;
    }

    public int size() {
        return size;
    }

    public void clear() {
        for (int i = 0; i < size; i++) {
            elements[i] = null;
        }
        size = 0;
    }

    @SuppressWarnings("unchecked")
    private void grow() {
        int newCapacity = elements.length * 2;
        Object[] grown = new Object[newCapacity];
        for (int i = 0; i < size; i++) {
            grown[i] = elements[i];
        }
        elements = (T[]) grown;
    }
}
