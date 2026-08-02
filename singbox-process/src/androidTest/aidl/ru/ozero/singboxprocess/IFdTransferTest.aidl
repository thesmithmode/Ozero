package ru.ozero.singboxprocess;

import android.os.ParcelFileDescriptor;
import ru.ozero.enginesingbox.ISingboxProtector;

interface IFdTransferTest {
    ParcelFileDescriptor transfer(ISingboxProtector protector);
}
