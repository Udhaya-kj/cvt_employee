package com.cvt.employee.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy.Companion.REPLACE
import androidx.room.Query

@Dao
interface EmployeeDao {
    @Query("SELECT * FROM cvt_employee")
    suspend fun getEmployee(): Employee

    @Insert(onConflict = REPLACE)
    suspend fun insertEmployee(employee: Employee)

    @Query("UPDATE cvt_employee SET ProductiveTime = :productiveTime WHERE id =:id")
    suspend fun updateProductive(id: Int, productiveTime: Long)

    @Query("UPDATE cvt_employee SET BreakTime = :breakTime WHERE id =:id")
    suspend fun updateBreak(id: Int, breakTime: Long)

    @Query("UPDATE cvt_employee SET CheckOutTime = :checkOutTime WHERE id =:id")
    suspend fun updateCheckOutTime(id: Int, checkOutTime: String)

    @Query("DELETE FROM cvt_employee")
    suspend fun deleteEmployee()
}