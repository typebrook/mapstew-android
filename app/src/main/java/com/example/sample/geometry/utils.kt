package com.example.sample.geometry

// transform raw coordinates to readable integer string
// example: (123456, 7654321) -> (123-456, 7654-321)
val xy2IntString: CoordPrinter = { (x, y) ->
    val raw2IntString = { raw: Double ->
        raw.toInt().toString().run {
            if (this.length <= 3) return@run this
            dropLast(3) + "-" + takeLast(3)
        }
    }
    x.let(raw2IntString) to y.let(raw2IntString)
}

// transform raw coordinates to Latitude/Longitude string with Degree format
// example: (123.456789123, 76.543219876) -> (東經 123.456-789 度, 北緯 76.543-219 度)
val xy2DegreeString: CoordPrinter = { (lon, lat) ->

    val lonPrefix = if (lon >= 0) "東經" else "西經"
    val latPrefix = if (lat >= 0) "北緯" else "南緯"

    val lonString = lon
            .let(Math::abs)
            .with("%.6f")
            .run { dropLast(3).padStart(7, '0') + "-" + takeLast(3) }
    val latString = lat
            .let(Math::abs)
            .with("%.6f")
            .run { dropLast(3).padStart(7, '0') + "-" + takeLast(3) }

    "$lonPrefix $lonString 度" to "$latPrefix $latString 度"
}

typealias dmValue = Pair<Int, Double>
val degree2DM: (Double) -> dmValue = { degree ->
    val dValue = degree.toInt()
    val mValue = (degree - dValue) * 60
    dValue to mValue
}

// transform raw coordinates to Latitude/Longitude string with Degree/Minute format
// example: (123.456789123, 76.543219876) -> (東經 123度 XX.XXX分, 北緯 76度 XX.XXX分)
val xy2DegMinString: CoordPrinter = { (lon, lat) ->

    val lonPrefix = if (lon >= 0) "東經" else "西經"
    val latPrefix = if (lat >= 0) "北緯" else "南緯"

    val dm2String = { (d, m): dmValue ->
        "${d}度 ${m.with("%.3f").padStart(6, '0')}分"
    }

    val xString = "$lonPrefix ${lon.let(Math::abs).let(degree2DM).let(dm2String)}"
    val yString = "$latPrefix ${lat.let(Math::abs).let(degree2DM).let(dm2String)}"
    xString to yString
}

typealias dmsValue = Triple<Int, Int, Double>
val degree2DMS: (Double) -> dmsValue = { degree ->
    val dValue = degree.toInt()
    val mValue = ((degree - dValue) * 60).toInt()
    val minute2Degree = mValue.toFloat() / 60
    val sValue = (degree - dValue - minute2Degree) * 3600
    Triple(dValue, mValue, sValue)
}

// transform raw coordinates to Latitude/Longitude string with Degree/Minute/Second format
// example: (123.456789123, 76.543219876) -> (東經 123度 XX分 XX.X秒, 北緯 76度 XX分 XXX秒)
val xy2DMSString: CoordPrinter = { (lon, lat) ->

    val lonPrefix = if (lon >= 0) "東經" else "西經"
    val latPrefix = if (lat >= 0) "北緯" else "南緯"

    val dms2String = { (d, m, s): dmsValue ->
        "${d}度 ${m.toString().padStart(2, '0')}分 ${s.with("%.1f").padStart(4, '0')}秒"
    }

    val xString = "$lonPrefix ${lon.let(Math::abs).let(degree2DMS).let(dms2String)}"
    val yString = "$latPrefix ${lat.let(Math::abs).let(degree2DMS).let(dms2String)}"
    xString to yString
}

fun Double.with(format: String): String = format.format(this)
fun Float.with(format: String): String = format.format(this)
