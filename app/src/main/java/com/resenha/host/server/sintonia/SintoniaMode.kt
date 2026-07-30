package com.resenha.host.server.sintonia

/** Which spectrum deck a match draws from: the everyday one, the spicier one, or both. */
enum class SintoniaMode(val label: String) {
    CLASSICO("Clássico"),
    PICANTE("Picante"),
    TUDO("Tudo");

    companion object {
        fun from(raw: String?): SintoniaMode =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: CLASSICO
    }
}
