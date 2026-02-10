Samsung Health Data SDK: Unified Guide

This document consolidates the fragmented pages of Samsung’s Health Data SDK documentation into a single scrollable reference. It covers the purpose of the SDK, supported data types, permission requirements, data‑access operations and filters, device management, code setup, and how to read, aggregate, insert, update and delete data. Citations are provided so you can navigate back to the original documentation if needed.

Overview

Samsung’s Health Data SDK provides Android developers with intuitive APIs for reading and writing health and fitness data stored in the Samsung Health app. It exposes high‑quality data recorded by a phone or wearable (Samsung Health version 6.30.2 or later, Android 10+). The SDK emphasizes data privacy and only allows access to data with explicit user consent. Key features include:

Reliable, rich data: heart‑rate measurements, body measurements, sleep data, workout sessions, nutrition and more. Data is averaged and processed when necessary, making it usable without device‑specific calibration.

High productivity: the API is built around Kotlin data classes (HealthDataStore, HealthDataPoint, Device, etc.), making it easy to read, aggregate, insert and update health data.

Data security & privacy: only data explicitly approved by the user can be accessed. The SDK does not support medical or diagnostic use.

Supported data categories are grouped into read and write types. Read‑only types include activity summaries, body measurements, heart rate, sleep, user profile and device information. Write types include blood glucose, blood oxygen, blood pressure, heart rate, nutrition, sleep, steps and various workout sessions.

Restrictions

Only physical Android devices are supported (no emulator support) and your app must be installed on the same phone that has Samsung Health installed.

Data from Samsung Health is intended for wellness, not medical decisions.

Data Types & Supported Operations

The data-types.html page lists available data types and which operations they support. Operations include read, aggregate, read changes, read associated data, insert, update and delete:

Data type (examples)	Supported operations (✓ = supported)
Activity summary	Read ✓, Aggregate ✓, Read changes ✓, Read associated data (N/A), Insert ✓, Update ✓, Delete ✓
Blood Glucose	Read ✓, Aggregate ✓, Read changes ✓, Insert ✓, Update ✓, Delete ✓
Blood Oxygen	Read ✓, Aggregate ✓, Read changes ✓, Insert ✓, Update ✓, Delete ✓
Blood Pressure	Read ✓, Aggregate ✓, Read changes ✓, Insert ✓, Update ✓, Delete ✓
Heart Rate	Read ✓, Aggregate ✓ (min/max), Read changes ✓, Read associated data ✓, Insert ✓, Update ✓, Delete ✓
Nutrition & water intake	Read ✓, Aggregate ✓, Read changes ✓, Insert ✓, Update ✓, Delete ✓
Sleep & sleep stages	Read ✓, Aggregate ✓, Read changes ✓, Read associated data ✓, Insert ✓, Update ✓, Delete ✓
Steps & walking/running	Read ✓, Aggregate ✓, Read changes ✓, Insert ✓, Update ✓, Delete ✓
User profile	Read ✓ only

The table above is abbreviated; refer to the original for a complete list of ~20 data types.

Data Permissions

An app must obtain explicit user consent for each data type it wants to access. When requesting permissions, the system shows a permission popup listing each requested data type (e.g., heart rate, steps). Developers must provide a description explaining why each permission is needed. Users can revoke permissions at any time. Key points:

Use requestPermissions() or requestPermissionsAsync() on the HealthDataStore instance to display the permission dialog.

Always offer a way for the user to change granted permissions in your app settings. Revoking permission may cause subsequent API calls to fail until re‑granted.

Attempting to read or write data without appropriate permission results in an AuthorizationException.

Data Access Operations

The data-access.html page describes how to read, aggregate, modify and delete health data. Key operations:

Reading data

To read data, build a read request specifying the data type and time range. The result is a list of HealthDataPoints representing each record. For example, reading heart rate data from the last week requires setting a time filter and optionally a source filter (e.g., only from a watch).

Aggregating data

Aggregation summarises multiple data points, such as total step counts or min/max heart rate. Use HealthDataStore.aggregateData() and specify the data type, grouping (minute, hour, day) and property (e.g., TOTAL for steps). Examples include:

Total steps by hour: set a LocalTimeFilter and group by hour using LocalTimeGroup.

Minimum and maximum heart rate: specify the heart rate data type and request MIN and MAX properties.

Last bedtime from sleep goal data: group by day and request the END_TIME property.

Reading changes

readChanges() returns a list of modifications (insert/update or delete events) that occurred within a given time interval. Use a ChangesRequest with start and end times. The response contains a changesToken for incremental syncing and a list of HealthDataChanges entries with a type (UPSERT or DELETE) and the affected UID.

Reading associated data

Some data types (like sleep) have associated data such as blood oxygen or heart rate recorded during that event. Use readAssociatedData() with an IdFilter specifying the parent record’s UID and addAssociatedDataType() for each associated data type. The API returns the requested associated data within the same time range.

Inserting data

To insert data:

Create or identify a source device using the DeviceManager. A Device object has properties like deviceId, deviceType (watch, accessory), manufacturer, model and user‑defined name. You can retrieve devices with getDevices(), getOwnDevices() or register a device using DeviceRegistrationRequest.registerDevice().

Build a HealthDataPoint using HealthDataPoint.builder(), set start/end times, and add data fields. For example, to insert blood glucose data you set GLUCOSE_LEVEL and SERIES_DATA fields.

Use HealthDataStore.insertData() with a list of insert requests. The API returns a list of inserted UIDs. If any insert fails, the whole batch fails.

Example code for inserting blood glucose data with device info:

val deviceManager = DeviceManager.getManager(applicationContext)
// Register watch and local phone as devices if needed
val devices = deviceManager.getOwnDevices()
val watchDevice = devices.find { it.deviceType == DeviceType.WATCH }
val phoneDevice = devices.find { it.deviceType == DeviceType.PHONE }

// Build blood glucose data with series
val dataPoint = HealthDataPoint.builder()
.setStartTime(startTime)
.setEndTime(endTime)
.setSourceDevice(watchDevice)
.addFieldData(DataType.BloodGlucoseType.GLUCOSE_LEVEL, 5.4f)
.addFieldData(DataType.BloodGlucoseType.SERIES_DATA, seriesData)
.build()

val insertRequest = DataTypes.BLOOD_GLUCOSE.insertDataRequestBuilder
.addData(dataPoint)
.build()
healthDataStore.insertData(insertRequest)

Filtering and Grouping

Filters allow you to narrow down queries:

Time filters: LocalTimeFilter, LocalDateFilter and InstantTimeFilter let you specify start and end times. LocalTimeFilter works with local times and time zones; InstantTimeFilter uses Instant. LocalDateFilter can filter by date ranges (e.g., the current week).

Source filters: ReadSourceFilter filters data by source application ID, device type, local device or platform (wearable vs phone). AggregateSourceFilter applies to aggregation queries.

ID filter: IdFilter.fromDataUid() or IdFilter.fromClientDataId() filters by a specific record ID.

Grouping organizes data for aggregation:

InstantTimeGroup: group by minute, hour or day using InstantTimeGroup.of(duration).

LocalDateGroup: group by day, week, month or year.

Device Manager

DeviceManager retrieves information about devices that produced health data. Each Device includes:

Unique device ID (deviceId)

Device type (e.g., phone, watch, accessory)

Manufacturer & model

User‑defined name

You can register a device using DeviceRegistrationRequest.registerDevice() with a device seed (e.g., MAC address). This is necessary when inserting data from devices not previously recognised. To read data from devices, use getDevices() or getOwnDevices() to obtain registered devices.

Hello Data SDK Quick‑Start (Programming Guide)

The Hello Data SDK pages provide step‑by‑step guidance on integrating the Health Data SDK into your app.

App module setup

Add the health-data-api AAR to your project and apply the Kotlin parcelize plugin in your module’s build.gradle:

plugins {
id 'kotlin-parcelize'
}
dependencies {
implementation files('libs/health-data-api-1.0.0.aar')
}


Getting a HealthDataStore

Call HealthDataService.getStore(context) to obtain a HealthDataStore instance. The returned store must be closed when no longer needed. Use this store for all subsequent operations such as checking permissions, reading, aggregating or writing data.

Checking and requesting permissions

Use healthDataStore.getGrantedPermissions() to check which permissions have been granted. If missing, call requestPermissions() from an Activity to launch the Samsung Health permission UI.

Exceptions may be thrown if the Samsung Health platform is not installed, out of date, disabled or not initialised. Handle ResolvablePlatformException codes like ERR_PLATFORM_NOT_INSTALLED, ERR_OLD_VERSION_PLATFORM, ERR_PLATFORM_DISABLED, ERR_PLATFORM_NOT_INITIALIZED.

Request permissions using Permission.of(DataType, AccessType) specifying read or write access for each data type.

Reading data

To read data, build a request with optional filters and ordering. For example, reading heart‑rate data from the last 60 minutes recorded by a watch:

val store = HealthDataService.getStore(context)
val readRequest = DataTypes.HEART_RATE.readDataRequestBuilder
.setTimeFilter(LocalTimeFilter.between(startTime, endTime))
.setSourceFilter(ReadSourceFilter.fromDeviceType(DeviceType.WATCH))
.setOrdering(Ordering.ASC)
.build()
val readResponse = store.readData(readRequest)
val dataList = readResponse.dataList // list of HealthDataPoint


Aggregating data

For aggregate operations, specify the grouping and properties. Example: total steps per hour across the past day:

val aggRequest = DataTypes.STEPS.aggregateRequestBuilder
.setLocalTimeFilterWithGroup(
LocalTimeFilter.between(startOfDay, endOfDay),
LocalTimeGroup.hourly()
)
.addProperty(Property.ofTotal(DataType.StepsType.COUNT))
.build()
val aggResult = store.aggregateData(aggRequest)


Reading changes

Use readChanges() to sync incremental updates. Provide a start time (lastSyncTime) and end time. The response includes a new changesToken for the next sync and a list of change records (upsert or delete).

Reading associated data

After reading an event (such as a sleep session), call readAssociatedData() with an IdFilter referencing the parent data and specify associated data types (e.g., blood oxygen). The result returns associated measurements within the same time range.

Inserting data

See the Inserting data section above for general guidance. In the Hello Data guide the sample shows retrieving watch and phone devices, constructing BloodGlucose points, building a request and calling healthDataStore.insertData().

Updating data

To update data, you need either the data’s uid assigned by Samsung Health or your own clientDataId if you set it on insert. Create a new HealthDataPoint with updated values and build an update request:

val updatedData = HealthDataPoint.builder()
.setStartTime(startTime)
.setEndTime(endTime)
.addFieldData(DataType.BloodGlucoseType.GLUCOSE_LEVEL, newAverage)
.addFieldData(DataType.BloodGlucoseType.SERIES_DATA, seriesData)
.build()
val updateRequest = DataTypes.BLOOD_GLUCOSE.updateDataRequestBuilder
.addDataWithClientDataId(clientDataId = "My ID", data = updatedData)
.build()
store.updateData(updateRequest)


Notes:

Only data that your app inserted can be updated. Select records by uid or your own clientDataId.

If any entry in the batch fails, none of the updates are applied.

Deleting data

Deleting is similar to updating: you can only delete records inserted by your app. Attempting to delete data from another source yields AuthorizationException error code 2002: ERR_NO_OWNERSHIP_TO_WRITE. Steps:

Find the UID of the data to delete by reading your own data with ReadSourceFilter.fromApplicationId(appName), ordering descending and limiting to 1 to get the latest record.

Build a delete request specifying the data type and an IdFilter.fromDataUid() with the UID.

Call healthDataStore.deleteData() to delete the data.

Example:

val latestUid = readLatestBloodGlucoseUid(appName)
val idFilter = IdFilter.fromDataUid(latestUid)
val deleteRequest = DataTypes.BLOOD_GLUCOSE.deleteDataRequestBuilder
.setIdFilter(idFilter)
.build()
store.deleteData(deleteRequest)


Error Handling

Exceptions thrown by SDK operations include:

AuthorizationException: thrown when you lack permission or try to modify another app’s data. Check error code 2002: ERR_NO_OWNERSHIP_TO_WRITE for delete/update operations.

ResolvablePlatformException: thrown when the Samsung Health platform is not installed, outdated, disabled, or not initialised. Handle these exceptions by showing the user a dialog to install/update/enable Samsung Health.

Generic Exception: covers network or platform errors; always catch and handle gracefully when calling insertData(), updateData(), deleteData() or other asynchronous functions.

Conclusion

Samsung’s Health Data SDK enables Android apps to access and manage health and fitness data recorded by Samsung devices. To use it effectively:

Import the SDK and obtain a HealthDataStore instance.

Request permissions for each data type needed and handle user consent.

Use filters and grouping to retrieve the right data at the right granularity.

Insert, update and delete only your own data, tracking records via uid or clientDataId.

Handle errors and device registration gracefully.

This consolidated guide should make navigating the Samsung documentation simpler, allowing you to focus on building your health‑centric application.