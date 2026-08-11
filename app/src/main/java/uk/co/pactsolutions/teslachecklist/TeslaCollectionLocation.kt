package uk.co.pactsolutions.teslachecklist

data class TeslaCollectionLocation(
    val id: String,
    val countryCode: String,
    val countryName: String,
    val region: String? = null,
    val city: String,
    val locationName: String,
    val addressLine1: String,
    val addressLine2: String? = null,
    val postcode: String,
    val locationType: TeslaLocationType,
    val newVehicleCollection: Boolean,
    val usedVehicleCollection: Boolean? = null,
    val serviceCentre: Boolean,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val teslaUrl: String? = null,
    val lastVerified: String,
    val verificationStatus: VerificationStatus
) {
    fun formattedAddress(): String = listOfNotNull(
        addressLine1,
        addressLine2,
        city,
        postcode,
        countryName
    ).joinToString(", ")
}

enum class TeslaLocationType {
    COLLECTION_POINT,
    DELIVERY_CENTRE,
    TESLA_CENTRE,
    SERVICE_AND_DELIVERY_CENTRE
}

enum class VerificationStatus {
    OFFICIALLY_CONFIRMED,
    REQUIRES_CONFIRMATION
}
