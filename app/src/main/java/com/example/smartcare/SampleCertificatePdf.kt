package com.example.smartcare

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import java.io.File
import java.io.OutputStream

/** Produces fictional certificates only. The watermark is drawn onto the PDF page. */
internal object SampleCertificatePdf {
    fun write(certificate: SampleMedicalCertificate, output: OutputStream) {
        val document = PdfDocument()
        try {
            val page = document.startPage(PdfDocument.PageInfo.Builder(595, 842, 1).create())
            draw(page.canvas, certificate)
            document.finishPage(page)
            document.writeTo(output)
        } finally {
            document.close()
        }
    }

    fun preview(certificate: SampleMedicalCertificate, cacheDir: File): Bitmap {
        val file = File.createTempFile("sample-mc-", ".pdf", cacheDir)
        try {
            file.outputStream().use { write(certificate, it) }
            val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer = try {
                PdfRenderer(descriptor)
            } catch (error: Exception) {
                descriptor.close()
                throw error
            }
            return renderer.use { pdf ->
                pdf.openPage(0).use { page ->
                    Bitmap.createBitmap(page.width * 2, page.height * 2, Bitmap.Config.ARGB_8888).apply {
                        eraseColor(Color.WHITE)
                        page.render(this, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    }
                }
            }
        } finally {
            file.delete()
        }
    }

    private fun draw(canvas: Canvas, mc: SampleMedicalCertificate) {
        val navy = Color.rgb(11, 42, 91)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        fun text(value: String, y: Float, size: Float = 13f, color: Int = navy) {
            paint.color = color
            paint.textSize = size
            canvas.drawText(value, 48f, y, paint)
        }
        fun value(label: String, value: String, y: Float) {
            text(label, y, 11f)
            val layout = StaticLayout.Builder.obtain(
                value, 0, value.length,
                TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = navy; textSize = 15f },
                499
            ).setAlignment(Layout.Alignment.ALIGN_NORMAL).build()
            canvas.save()
            canvas.translate(48f, y + 6)
            layout.draw(canvas)
            canvas.restore()
        }
        val format = SampleMedicalCertificate.dateFormat
        paint.typeface = Typeface.DEFAULT_BOLD
        text("SmartCare Demo Clinic", 66f, 24f)
        paint.typeface = Typeface.DEFAULT
        text("Fictional clinic details • Kuala Lumpur, Malaysia", 90f)
        canvas.drawLine(48f, 107f, 547f, 107f, paint)
        text("MEDICAL CERTIFICATE", 146f, 22f)
        text("SAMPLE / NOT VALID", 170f, 14f, Color.rgb(145, 31, 31))
        text("Reference placeholder: ${mc.reference}", 198f)
        value("Patient name", mc.patient, 233f)
        value("Doctor name", mc.doctor, 320f)
        value("Issue date", mc.issue.format(format), 407f)
        value("Medical leave duration", "${mc.days} day(s)", 455f)
        value("Medical leave start date", mc.start.format(format), 503f)
        value("Medical leave end date", mc.end.format(format), 551f)
        value("Expected return to work", mc.returnToWork.format(format), 599f)
        paint.color = navy
        paint.style = Paint.Style.STROKE
        canvas.drawRect(48f, 655f, 280f, 744f, paint)
        canvas.drawRect(315f, 655f, 547f, 744f, paint)
        paint.style = Paint.Style.FILL
        text("Doctor signature placeholder", 676f, 11f)
        canvas.save()
        canvas.translate(267f, 0f)
        text("Clinic stamp placeholder", 676f, 11f)
        canvas.restore()
        text("Fictional prototype document. No signature or clinic stamp.", 789f, 11f)

        canvas.save()
        canvas.rotate(-29f, 297f, 420f)
        paint.typeface = Typeface.DEFAULT_BOLD
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = 46f
        paint.color = Color.argb(55, 145, 31, 31)
        canvas.drawText("SAMPLE / NOT VALID", 297f, 436f, paint)
        canvas.restore()
    }
}
