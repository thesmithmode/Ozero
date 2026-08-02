package ru.ozero.enginesingbox;

import android.os.ParcelFileDescriptor;

interface ISingboxProtector {
    boolean protect(in ParcelFileDescriptor socket);
}
