package com.pp68.backend.domain.repository

import com.pp68.backend.domain.entity.ContactPerson

interface ContactPersonRepository {
    suspend fun findByCustId(custId: String): List<ContactPerson>
    suspend fun findByUserId(userId: String): List<ContactPerson>
    suspend fun findByCustIds(custIds: List<String>): List<ContactPerson>
    suspend fun findById(contactId: String): ContactPerson?
    suspend fun create(contact: ContactPerson): ContactPerson
    suspend fun update(contactId: String, updates: Map<String, Any?>): ContactPerson?
    suspend fun delete(contactId: String): Boolean
    suspend fun deleteByCustId(custId: String): Boolean
}