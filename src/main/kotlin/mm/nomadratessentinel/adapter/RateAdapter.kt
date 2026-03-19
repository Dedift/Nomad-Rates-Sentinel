package mm.nomadratessentinel.adapter

import mm.nomadratessentinel.model.ParsedRate


interface RateAdapter {
    fun fetchRates(): List<ParsedRate>
}