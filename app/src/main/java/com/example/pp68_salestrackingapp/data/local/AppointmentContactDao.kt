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
}
