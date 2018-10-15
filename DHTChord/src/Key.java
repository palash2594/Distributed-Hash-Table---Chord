
public class Key {
    int id;
    String message;

    public Key(int id, String message) {
        this.id = id;
        this.message = message;
    }

    @Override
    public String toString() {
        return "Key{" +
                "id=" + id +
                ", message='" + message + '\'' +
                '}';
    }
}
