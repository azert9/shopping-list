package fr.jloc.shoppinglist.business

interface App {

    val conditionsAccepted: Boolean
    fun setConditionsAccepted()

    val lastOpenedPadId: String?
    fun setLastOpenedPadId(id: String)

    val pads: PadsManager
}
