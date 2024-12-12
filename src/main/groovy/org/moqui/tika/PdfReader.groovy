package org.moqui.tika
import org.apache.tika.Tika
import java.nio.file.Paths

class PdfReader {
    static String extractTextFromPdf(String pdfPath) {
        Tika tika = new Tika()
        File pdfFile = new File(pdfPath)
        return tika.parseToString(pdfFile)
    }
}
