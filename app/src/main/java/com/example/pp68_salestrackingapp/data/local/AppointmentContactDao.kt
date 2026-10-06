package com.example.pp68_salestrackingapp.data.local

import androidx.room.*
import com.example.pp68_salestrackingapp.data.model.AppointmentContact

@Dao
interface AppointmentContactDao {
    @Query("SELECT * FROM appointment_contact WHERE appointment_id = :appointmentId")
    suspend fun getContactsByAppointmentId(appointmentId: String): List<AppointmentContact>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAppointmentContacts(contacts: List<AppointmentContact>)

    @Query("DELETE FROM appointment_contact WHERE appointment_id = :appointmentId")
    suspend fun deleteContactsByAppointmentId(appointmentId: String)

    @Query("DELETE FROM appointment_contact WHERE appointment_id = :appointmentId AND contact_id = :contactId")
    suspend fun delete(appointmentId: String, contactId: String)

    @Query("UPDATE appointment_contact SET appointment_id = :newId WHERE appointment_id = :oldId")
    suspend fun updateAppointmentId(oldId: String, newId: String)

    @Query("SELECT * FROM appointment_contact")
    suspend fun getAll(): List<AppointmentContact>

    /** Replaces only server-owned relations; TEMP appointment relations are offline outbox data. */
    @Query(
        """DELETE FROM appointment_contact
           WHERE appointment_id IN (
               SELECT appointment_id FROM activity_table WHERE is_synced = 1
           )"""
    )
    suspend fun deleteForSyncedAppointments()
}
