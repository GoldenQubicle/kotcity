package kotcity.data

import kotcity.util.getRandomElement

enum class BuildingType {
    ROAD, RESIDENTIAL, COMMERCIAL, INDUSTRIAL, POWER_LINE, POWER_PLANT, CIVIC
}

enum class ZoneType {
    RESIDENTIAL, COMMERCIAL, INDUSTRIAL
}

val POWER_PLANT_TYPES = listOf("coal", "nuclear")

data class Zone(val type: ZoneType)

enum class Tradeable {
    MONEY,
    GOODS,
    LABOR,
    RAW_MATERIALS,
    WHOLESALE_GOODS
}

data class Location(val coordinate: BlockCoordinate, val building: Building)

data class Contract(
        val to: Location,
        val from: Location,
        val tradeable: Tradeable,
        val quantity: Int

) {
    override fun toString(): String {
        return "Contract(to=${to.building.description} from=${from.building.description} tradeable=$tradeable quantity=$quantity)"
    }
}

abstract class Building(private val cityMap: CityMap) {
    abstract var width: Int
    abstract var height: Int
    abstract var type: BuildingType
    open val variety: String? = null
    open var name: String? = null
    open var sprite: String? = null
    open var description: String? = null
    var powered = false
    open val powerRequired = 0
    val consumes: MutableMap<Tradeable, Int> = mutableMapOf()
    val produces: MutableMap<Tradeable, Int> = mutableMapOf()
    open var upkeep: Int = 0
    private val contracts: MutableList<Contract> = mutableListOf()

    fun summarizeContracts(): String {
        val summaryBuffer = StringBuffer()

        val tradeables = consumes.keys.distinct()
        tradeables.forEach {
            summaryBuffer.append("Consumes: ${consumes[it]} $it\n")
        }

        produces.keys.distinct().forEach {
            summaryBuffer.append("Produces: ${produces[it]} $it\n")
        }

        contracts.forEach {
            // summaryBuffer.append(it.toString() + "\n")
            if (it.to.building == this) {
                summaryBuffer.append("Receiving ${it.quantity} ${it.tradeable} from ${it.from.building.description}\n")
            }
            if (it.from.building == this) {
                summaryBuffer.append("Sending ${it.quantity} ${it.tradeable} to ${it.to.building.description}\n")
            }
        }
        return summaryBuffer.toString()
    }

    fun quantityForSale(tradeable: Tradeable): Int {
        val filter = {contract: Contract -> contract.from.building }
        val hash = produces
        return calculateAvailable(hash, tradeable, filter)
    }

    fun quantityWanted(tradeable: Tradeable): Int {
        val inventoryCount = consumes[tradeable] ?: 0
        val contractCount = contracts.filter { it.to.building == this && it.tradeable == tradeable }.map { it.quantity }.sum()
        return inventoryCount - contractCount
    }

    private fun calculateAvailable(hash: MutableMap<Tradeable, Int>, tradeable: Tradeable, filter: (Contract) -> Building): Int {
        val inventoryCount = hash[tradeable] ?: 0
        val contractCount = contracts.filter { filter(it) == this && it.tradeable == tradeable }.map { it.quantity }.sum()
        return inventoryCount - contractCount
    }

    private fun addContract(contract: Contract) {
        this.contracts.add(contract)
    }

    fun createContract(otherBuilding: Building, tradeable: Tradeable, quantity: Int) {
        val ourBlocks = cityMap.coordinatesForBuilding(this) ?: return
        val theirBlocks = cityMap.coordinatesForBuilding(otherBuilding) ?: return
        val ourLocation = Location(ourBlocks, this)
        val theirLocation = Location(theirBlocks, otherBuilding)
        val newContract = Contract(ourLocation, theirLocation, tradeable, quantity)
        // TODO: check to make sure we actually have this amount...
        if (otherBuilding.quantityForSale(tradeable) >= newContract.quantity) {
            contracts.add(newContract)
            otherBuilding.addContract(newContract)
        } else {
            println("Tried to make an invalid contract: $newContract but failed because ${this.name} doesn't have enough $tradeable")
        }
    }

    fun voidContractsWith(otherBuilding: Building, reciprocate: Boolean = true) {
        contracts.removeAll {
            it.to.building == otherBuilding || it.from.building == otherBuilding
        }
        if (reciprocate) {
            otherBuilding.voidContractsWith(this, false)
        }
    }

    fun needs(tradeable: Tradeable): Int {
        val requiredCount = consumes[tradeable] ?: return 0
        val contractCount = contracts.filter { it.to.building == this && it.tradeable == tradeable }.map { it.quantity }.sum()
        return requiredCount - contractCount
    }

    fun voidRandomContract() {
        if (contracts.count() > 0) {
            val contractToKill = contracts.getRandomElement()
            voidContractsWith(contractToKill.to.building)
        }
    }
}

open class PowerPlant : Building {

    override val variety: String
    var powerGenerated: Int = 0

    constructor(variety: String, cityMap: CityMap) : super(cityMap) {
        if (!POWER_PLANT_TYPES.contains(variety)) {
            throw RuntimeException("Invalid power plant type: $variety")
        }
        this.variety = variety
        when (variety) {
            "coal" -> { this.powerGenerated = 2000; this.description = "Coal Power Plant" }
            "nuclear" -> { this.powerGenerated = 5000; this.description = "Nuclear Power Plant" }
        }
        this.type = BuildingType.POWER_PLANT
    }

    override var type: BuildingType
    override var width = 4
    override var height = 4
}

class Road(cityMap: CityMap) : Building(cityMap) {
    override var width = 1
    override var height = 1
    override var type = BuildingType.ROAD
    override var description: String? = "Road"
}

class PowerLine(cityMap: CityMap) : Building(cityMap) {
    override var type: BuildingType = BuildingType.POWER_LINE
    override var width = 1
    override var height = 1
    override val powerRequired = 1
    override var description: String? = "Power Line"
}

class LoadableBuilding(cityMap: CityMap) : Building(cityMap) {
    var level: Int = 1
    override var height: Int = 1
    override var width: Int = 1
    override lateinit var type: BuildingType
}
