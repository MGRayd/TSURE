package uk.co.pactsolutions.teslachecklist

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.Locale

interface TeslaLocationRepository {
    suspend fun getAllLocations(): List<TeslaCollectionLocation>
    suspend fun getLocationsByCountry(countryCode: String): List<TeslaCollectionLocation>
    suspend fun searchLocations(query: String): List<TeslaCollectionLocation>
    suspend fun getLocationById(id: String): TeslaCollectionLocation?
}

class AssetTeslaLocationRepository(context: Context) : TeslaLocationRepository {
    private val locations: List<TeslaCollectionLocation> by lazy {
        val json = context.applicationContext.assets
            .open("tesla_collection_locations.json")
            .bufferedReader()
            .use { it.readText() }
        val type = object : TypeToken<List<TeslaCollectionLocation>>() {}.type
        Gson().fromJson(json, type)
    }

    fun getAllLocationsNow(): List<TeslaCollectionLocation> = locations

    override suspend fun getAllLocations(): List<TeslaCollectionLocation> = locations

    override suspend fun getLocationsByCountry(countryCode: String): List<TeslaCollectionLocation> =
        locations.filter { it.countryCode.equals(countryCode, ignoreCase = true) }

    override suspend fun searchLocations(query: String): List<TeslaCollectionLocation> {
        val needle = query.trim().lowercase(Locale.ROOT)
        if (needle.isEmpty()) return locations
        return locations.filter {
            listOf(it.locationName, it.city, it.postcode, it.countryName)
                .any { value -> value.lowercase(Locale.ROOT).contains(needle) }
        }
    }

    override suspend fun getLocationById(id: String): TeslaCollectionLocation? =
        locations.firstOrNull { it.id == id }
}
