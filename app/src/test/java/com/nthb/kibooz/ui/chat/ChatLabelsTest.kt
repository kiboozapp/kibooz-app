package com.nthb.kibooz.ui.chat

import com.nthb.kibooz.R
import com.nthb.kibooz.data.remote.ChatMedia
import com.nthb.kibooz.ui.chat.ChatFixtures.contact
import com.nthb.kibooz.ui.chat.ChatFixtures.message
import com.nthb.kibooz.ui.chat.ChatFixtures.person
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChatLabelsTest {

    private val utc = TimeZone.getTimeZone("UTC")
    private val now = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US).parse("2026-11-25T12:00:00Z")!!

    // Pratinjau pesan

    @Test
    fun `a text message previews as text`() {
        assertEquals(PreviewKind.TEXT, message(1, body = "halo").previewKind())
    }

    @Test
    fun `a deleted message previews as deleted even if it still had media`() {
        assertEquals(PreviewKind.DELETED, message(1, deleted = true).previewKind())
        assertEquals(PreviewKind.DELETED, message(1, deleted = true, media = ChatMedia(MEDIA_PHOTO)).previewKind())
    }

    @Test
    fun `a message with only a photo or a video previews by its kind`() {
        assertEquals(PreviewKind.PHOTO, message(1, body = "", media = ChatMedia(MEDIA_PHOTO)).previewKind())
        assertEquals(PreviewKind.VIDEO, message(1, body = "", media = ChatMedia(MEDIA_VIDEO)).previewKind())
        assertEquals(PreviewKind.ATTACHMENT, message(1, body = "", media = ChatMedia(MEDIA_AUDIO)).previewKind())
    }

    @Test
    fun `text with media previews as text and an empty message previews as empty`() {
        assertEquals(PreviewKind.TEXT, message(1, body = MSG_LOOK, media = ChatMedia(MEDIA_PHOTO)).previewKind())
        assertEquals(PreviewKind.EMPTY, message(1, body = "  ").previewKind())
    }

    // Lampiran

    @Test
    fun `a bubble shows a photo or a video, and nothing for a deleted or unknown attachment`() {
        assertEquals(MEDIA_PHOTO, message(1, body = MSG_LOOK, media = ChatMedia(MEDIA_PHOTO)).shownMedia()?.kind)
        assertEquals(MEDIA_VIDEO, message(1, body = "", media = ChatMedia(MEDIA_VIDEO)).shownMedia()?.kind)
        assertNull(message(1, deleted = true, media = ChatMedia(MEDIA_PHOTO)).shownMedia())
        assertNull(message(1, media = ChatMedia(MEDIA_AUDIO)).shownMedia())
        assertNull(message(1).shownMedia())
    }

    @Test
    fun `a file size reads in KB below one MB and in MB with an Indonesian comma above`() {
        assertEquals("1 KB", fileSizeLabel(1))
        assertEquals("320 KB", fileSizeLabel(320L * 1024))
        assertEquals("3,2 MB", fileSizeLabel(3_354_000))
        assertEquals("25,0 MB", fileSizeLabel(25L * 1024 * 1024))
    }

    // Nama

    @Test
    fun `a deleted account shows the local label and not the name from the server`() {
        assertEquals(LABEL_DELETED, person(name = "Deleted account", deleted = true).displayName(LABEL_DELETED))
        assertEquals(NAME_BU_SARI, person(name = NAME_BU_SARI).displayName(LABEL_DELETED))
    }

    @Test
    fun `a parent contact shows the names of the children, a teacher contact shows only the name`() {
        assertEquals("Siti (Rani)", contact(ID_U1, name = "Siti", children = listOf("Rani")).title())
        assertEquals("Siti (Rani, Dika)", contact(ID_U1, name = "Siti", children = listOf("Rani", "Dika")).title())
        assertEquals(NAME_BU_SARI, contact("u2", name = NAME_BU_SARI).title())
        assertEquals(
            NAME_TEACHER_SITI,
            contact("u3", name = NAME_TEACHER_SITI, children = listOf(CHILD_ADIT))
                .copy(role = ROLE_GURU)
                .title()
        )
        assertEquals(
            "Wali Kelas 1A (Aisyah Pratama +2 anak)",
            contact(
                "u4",
                name = "Wali Kelas 1A",
                children = listOf("Aisyah Pratama", "Akbar Ramadhan", "Alif Rahman")
            ).title()
        )
    }

    @Test
    fun `contact subtitle includes explicit role and shared classes`() {
        val guru = contact("u3", name = NAME_TEACHER_SITI, children = listOf(CHILD_ADIT)).copy(role = ROLE_GURU)
        val wali = contact(ID_U1, name = "Hendra", children = listOf(CHILD_ADIT))

        assertEquals("$LABEL_GURU • Bunga", guru.subtitle(LABEL_GURU, LABEL_WALI))
        assertEquals("$LABEL_WALI • Bunga", wali.subtitle(LABEL_GURU, LABEL_WALI))
        assertEquals(LABEL_GURU, guru.copy(classes = emptyList()).subtitle(LABEL_GURU, LABEL_WALI))
    }

    @Test
    fun `the shared classes are listed with commas`() {
        assertEquals("Bunga", contact("u2").classNames())
        assertEquals("", contact("u2").copy(classes = emptyList()).classNames())
    }

    @Test
    fun `initials come from the first two words`() {
        assertEquals("BS", initialsOf(NAME_BU_SARI))
        assertEquals("BS", initialsOf("budi santoso wijaya"))
        assertEquals("S", initialsOf("siti"))
        assertEquals("AB", initialsOf("  ani   budi "))
        assertEquals("", initialsOf(""))
        assertEquals("", initialsOf("   "))
    }

    // Waktu di daftar

    @Test
    fun `a message from today shows the hour and a message from another day shows the date`() {
        assertEquals("07:05", listTimeLabel("2026-11-25T07:05:00Z", now, utc))
        assertEquals("24 Nov 2026", listTimeLabel("2026-11-24T23:59:00Z", now, utc))
        assertEquals("1 Jan 2026", listTimeLabel("2026-01-01T00:00:00Z", now, utc))
    }

    @Test
    fun `today follows the zone of the person`() {
        val jakarta = TimeZone.getTimeZone("Asia/Jakarta")

        // 18:30 UTC on the 24th is 01:30 on the 25th in Jakarta, so it is today there and yesterday in UTC
        assertEquals("01:30", listTimeLabel("2026-11-24T18:30:00Z", now, jakarta))
        assertEquals("24 Nov 2026", listTimeLabel("2026-11-24T18:30:00Z", now, utc))
    }

    @Test
    fun `a time that cannot be read gives null`() {
        assertNull(listTimeLabel("kemarin", now, utc))
    }

    // Alasan percakapan ditutup

    @Test
    fun `the reason a conversation is closed follows its kind and the state of the other account`() {
        assertEquals(R.string.chat_ditutup_kelas_arsip, closedReasonRes(isRoom = true, peerDeleted = false))
        assertEquals(R.string.chat_ditutup_kelas_arsip, closedReasonRes(isRoom = true, peerDeleted = true))
        assertEquals(R.string.chat_ditutup_akun_dihapus, closedReasonRes(isRoom = false, peerDeleted = true))
        assertEquals(R.string.chat_ditutup_tidak_sekelas, closedReasonRes(isRoom = false, peerDeleted = false))
    }

    private companion object {
        const val MEDIA_PHOTO = "photo"
        const val MEDIA_VIDEO = "video"
        const val MEDIA_AUDIO = "audio"
        const val MSG_LOOK = "lihat"
        const val LABEL_DELETED = "Akun dihapus"
        const val NAME_BU_SARI = "Bu Sari"
        const val ID_U1 = "u1"
        const val NAME_TEACHER_SITI = "Ibu Siti Aminah, S.Pd"
        const val CHILD_ADIT = "Adit Pratama"
        const val ROLE_GURU = "GURU"
        const val LABEL_GURU = "Guru"
        const val LABEL_WALI = "Wali murid"
    }
}
