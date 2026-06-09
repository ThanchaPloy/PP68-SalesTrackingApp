package com.pp68.backend.data.repository

import com.pp68.backend.data.database.tables.ContactPersonTable
import com.pp68.backend.domain.entity.ContactPerson
import com.pp68.backend.domain.repository.ContactPersonRepository
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.time.Instant

class ContactPersonRepositoryImpl : ContactPersonRepository {

    private suspend fun <T> dbQuery(block: suspend () -> T): T =
        newSuspendedTransaction(Dispatchers.IO) { block() }

    private fun ResultRow.toContact() = ContactPerson(
        contactId = this[ContactPersonTable.contactId],
        custId    = this[ContactPersonTable.custId],
        userId    = this[ContactPersonTable.userId],
        fullName  = this[ContactPersonTable.fullName],
        role      = this[ContactPersonTable.role],
        phone     = this[ContactPersonTable.phone],
        email     = this[ContactPersonTable.email],
        lineId    = this[ContactPersonTable.lineId],
        createdAt = this[ContactPersonTable.createdAt]?.toString()
    )

    override suspend fun findByCustId(custId: String): List<ContactPerson> = dbQuery {
        ContactPersonTable.select { ContactPersonTable.custId eq custId }.map { it.toContact() }
    }

    override suspend fun findByUserId(userId: String): List<ContactPerson> = dbQuery {
        ContactPersonTable.select { ContactPersonTable.userId eq userId }.map { it.toContact() }
    }

    override suspend fun findByCustIds(custIds: List<String>): List<ContactPerson> = dbQuery {
        ContactPersonTable.select { ContactPersonTable.custId inList custIds }.map { it.toContact() }
    }

    override suspend fun findById(contactId: String): ContactPerson? = dbQuery {
        ContactPersonTable.select { ContactPersonTable.contactId eq contactId }.singleOrNull()?.toContact()
    }

    override suspend fun create(contact: ContactPerson): ContactPerson = dbQuery {
        ContactPersonTable.insert {
            it[contactId] = contact.contactId
            it[custId]    = contact.custId
            it[userId]    = contact.userId
            it[fullName]  = contact.fullName
            it[role]      = contact.role
            it[phone]     = contact.phone
            it[email]     = contact.email
            it[lineId]    = contact.lineId
            it[createdAt] = Instant.now()
        }
        ContactPersonTable.select { ContactPersonTable.contactId eq contact.contactId }.single().toContact()
    }

    override suspend fun update(contactId: String, updates: Map<String, Any?>): ContactPerson? = dbQuery {
        ContactPersonTable.update({ ContactPersonTable.contactId eq contactId }) { stmt ->
            updates["full_name"]?.let { v -> stmt[fullName] = v as String }
            updates["role"]?.let      { v -> stmt[role]     = v as String }
            updates["phone"]?.let     { v -> stmt[phone]    = v as String }
            updates["email"]?.let     { v -> stmt[email]    = v as String }
            updates["line_id"]?.let   { v -> stmt[lineId]   = v as String }
        }
        ContactPersonTable.select { ContactPersonTable.contactId eq contactId }.singleOrNull()?.toContact()
    }

    override suspend fun delete(contactId: String): Boolean = dbQuery {
        ContactPersonTable.deleteWhere { ContactPersonTable.contactId eq contactId } > 0
    }

    override suspend fun deleteByCustId(custId: String): Boolean = dbQuery {
        ContactPersonTable.deleteWhere { ContactPersonTable.custId eq custId } > 0
    }
}