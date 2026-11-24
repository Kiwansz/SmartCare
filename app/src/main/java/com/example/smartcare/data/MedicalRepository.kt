package com.example.smartcare.data

/**
 * A future implementation will reuse SupabaseProvider and the existing authenticated session.
 * The backend must derive the patient and doctor from the session and appointment, check
 * eligibility, and return a conflict if a record of the same type already exists.
 * MedicalJson prepares row mappings locally. Inserts omit sequence IDs and now() timestamps.
 * Use the returned prescription ID for its items within the same backend transaction.
 */
internal interface MedicalRepository {
    /** Return only the authenticated patient's consultations and visible documents. */
    suspend fun loadMyConsultations(): Result<List<PatientConsultation>>

    suspend fun createHistory(appointmentId: Int, input: MedicalHistoryInput): Result<MedicalHistoryRow>

    /** Save the parent prescription and all items in one transaction. */
    suspend fun createPrescription(
        appointmentId: Int,
        input: PrescriptionInput,
    ): Result<PrescriptionWithItems>

    suspend fun createCertificate(appointmentId: Int, input: MedicalCertificateInput): Result<MedicalCertificateRow>
}

/** All operations fail locally while medical access is disabled. */
internal object DisabledMedicalRepository : MedicalRepository {
    override suspend fun loadMyConsultations(): Result<List<PatientConsultation>> = disabled()

    override suspend fun createHistory(appointmentId: Int, input: MedicalHistoryInput): Result<MedicalHistoryRow> = disabled()

    override suspend fun createPrescription(
        appointmentId: Int,
        input: PrescriptionInput,
    ): Result<PrescriptionWithItems> = disabled()

    override suspend fun createCertificate(appointmentId: Int, input: MedicalCertificateInput): Result<MedicalCertificateRow> = disabled()

    private fun <T> disabled(): Result<T> =
        Result.failure(IllegalStateException("Live medical data access is disabled."))
}
