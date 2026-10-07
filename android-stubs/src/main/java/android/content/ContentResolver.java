package android.content;

import java.io.InputStream;
import java.io.IOException;

/** Desktop stub of ContentResolver: no content providers exist headless. */
public class ContentResolver {
    public InputStream openInputStream(java.net.URI uri) throws IOException {
        throw new IOException("no content providers on the desktop bridge");
    }

    public InputStream openInputStream(android.net.Uri uri) throws IOException {
        throw new IOException("no content providers on the desktop bridge");
    }
}
