package com.cvt.employee.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cvt_employee")
data class Employee(
    @PrimaryKey val Id: Int,
    @ColumnInfo(name = "ProductiveTime") val productiveTime: Long?,
    @ColumnInfo(name = "BreakTime") val breakTime: Long?,
    @ColumnInfo(name = "CheckInTime") val checkInTime: String?,
    @ColumnInfo(name = "CheckOutTime") val checkOutTime: String?
)
