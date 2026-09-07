package com.pashri.pdfmaps.data

/** Outcome of importing one PDF. */
sealed interface ImportResult {

    /**
     * The PDF was imported successfully.
     *
     * @property name Display name of the first imported page.
     * @property pageCount Number of library entries created.
     */
    data class Success(val name: String, val pageCount: Int) :
        ImportResult

    /**
     * The PDF could not be imported and nothing was kept.
     *
     * @property reason Message suitable for showing to the user.
     */
    data class Failure(val reason: String) : ImportResult
}
