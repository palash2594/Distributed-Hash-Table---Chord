package DHTChord;

public class Key {
    int id;
    String message;
    int size;

    public Key(int id, String message, int size) {
        this.id = id;
        this.message = message;
        this.size = size;
    }

    @Override
    public String toString() {
        return "Key{" +
                "id=" + id +
                ", message='" + message + '\'' +
                ", size=" + size +
                '}';
    }
}
