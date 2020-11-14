package com.carto.androidtest

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Query

const val CARTO_BASE_URL = "https://javieraragon.carto.com/api/v2/"
const val QUERY =
    "SELECT id, direction, href as image, region, title, view as description, " +
    "ST_X(the_geom) as longitude, ST_Y(the_geom) as latitude FROM ios_test"

@JsonClass(generateAdapter = true)
class PoisResponse<T>(
    @Json(name = "rows") val rows: List<T>,
    @Json(name = "total_rows") val totalRows: Long
)

@JsonClass(generateAdapter = true)
data class Poi(
    @Json(name = "id") val id: String,
    @Json(name = "title") val title: String,
    @Json(name = "description") val description: String,
    @Json(name = "direction") val direction: String,
    @Json(name = "region") val region: String,
    @Json(name = "image") val image: String,
    @Json(name = "longitude") val longitude: Double,
    @Json(name = "latitude") val latitude: Double
) {
    fun getImageFixed(): String = image.replace("http:", "https:")
}

interface PoisApi {
    @GET("sql")
    fun getPois(@Query("q") query: String): Call<PoisResponse<Poi>>
}