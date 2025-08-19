package Phase1;

public class RC4 {
    private byte[] S = new byte[256];
    private int x = 0;
    private int y = 0;

    public RC4() {
        // No-arg constructor
    }

    // Initialize/reset with key before encrypt/decrypt each time
    private void init(byte[] key) {
        for (int i = 0; i < 256; i++) {
            S[i] = (byte) i;
        }
        int j = 0;
        for (int i = 0; i < 256; i++) {
            j = (j + (S[i] & 0xff) + (key[i % key.length] & 0xff)) & 0xff;
            byte temp = S[i];
            S[i] = S[j];
            S[j] = temp;
        }
        x = 0;
        y = 0;
    }

    public byte[] encrypt(byte[] key, byte[] data) {
        init(key);
        byte[] output = new byte[data.length];
        for (int i = 0; i < data.length; i++) {
            output[i] = (byte) (data[i] ^ keyItem());
        }
        return output;
    }

    public byte[] decrypt(byte[] key, byte[] data) {
        return encrypt(key, data);
    }

    private byte keyItem() {
        x = (x + 1) & 0xff;
        y = (y + (S[x] & 0xff)) & 0xff;
        byte temp = S[x];
        S[x] = S[y];
        S[y] = temp;
        return S[(S[x] + S[y]) & 0xff];
    }
}
