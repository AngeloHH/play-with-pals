package com.boardgames


fun checkGame(name: String?, throwException: Boolean = false): Boolean {
    val regex = Regex("^[a-zA-Z0-9-]+$")
    val invalid = name.isNullOrBlank() || !name.matches(regex)
    if (invalid && !throwException) {
        throw IllegalArgumentException("Invalid game name.")
    }
    return invalid
}

fun findLine(coords: List<Pair<Int, Int>>, min: Int): Boolean {
    // Define offsets for neighboring points in different directions.
    val offsets = listOf(Pair(1, 1), Pair(-1, -1), Pair(1, 0), Pair(0, 1), Pair(-1, 0), Pair(0, -1))
    var options = mutableListOf<Pair<Int, Int>>()
    var selected = -1
    val line = mutableListOf<Pair<Int, Int>>()

    // Main loop to find the line.
    while (true) {
        // Extend the line if it has at least two points
        if (line.size > 1) {
            val (firstPoint, secondPoint) = line.take(2)
            val diffY = secondPoint.second - firstPoint.second
            val diffX = secondPoint.first - firstPoint.first
            val (lastX, lastY) = line.last()
            val value = Pair(lastX + diffX, lastY + diffY)
            // Add the new point to the line if it is in the
            // coordinates, otherwise reset the line.
            if (value in coords) line.add(value)
            else line.apply { clear(); add(coords[selected]) }
        }
        if (selected + 1 >= coords.size || line.size >= min) break

        // Choose a new point from the adjacent points if there are no
        // options for the current point.
        if (options.isEmpty()) {
            selected++; line.apply { clear(); add(coords[selected]) }
            val next = offsets.map {(x, y) ->
                Pair(line.first().first + x, line.first().second + y)
            }
            options = next.filter { it in coords }.toMutableList()
        }

        // Add a new point from the adjacent points
        if (line.size == 1) options.removeFirstOrNull()?.let(line::add)
    }
    return line.size >= min
}