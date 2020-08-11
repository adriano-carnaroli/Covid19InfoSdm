package br.edu.ifsp.scl.sdm.covid19infosdm

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.lifecycle.Observer
import br.edu.ifsp.scl.sdm.covid19infosdm.model.dataclass.*
import br.edu.ifsp.scl.sdm.covid19infosdm.viewmodel.Covid19ViewModel
import com.jjoe64.graphview.helper.DateAsXAxisLabelFormatter
import com.jjoe64.graphview.series.DataPoint
import com.jjoe64.graphview.series.LineGraphSeries
import kotlinx.android.synthetic.main.activity_main.*
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {
    private lateinit var viewModel: Covid19ViewModel
    private lateinit var countryAdapter: ArrayAdapter<String>
    private lateinit var countryNameSlugMap: MutableMap<String, String>

    /* Classe para os serviços que serão acessados */
    private enum class Information(val type: String, val desc: String){
        DAY_ONE("Day one", "Por dia"),
        BY_COUNTRY("By country", "Por país")
    }

    /* Classe para o status que será buscado no serviço */
    private enum class Status(val type: String, val desc: String){
        CONFIRMED("Confirmed", "Confirmados"),
        RECOVERED("Recovered", "Recuperados"),
        DEATHS("Deaths", "Mortes")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        viewModel = Covid19ViewModel(this)

        countryAdapterInit()

        informationAdapterInit()

        statusAdapterInit()

        resultGv.gridLabelRenderer.setHumanRounding(false)
    }

    fun onRetrieveClick(view: View) {
        progressBar.visibility = View.VISIBLE
        if (infoSp.selectedItem.toString().equals(Information.DAY_ONE.desc)) { fetchDayOne() } else { fetchByCountry() }
    }

    private fun countryAdapterInit() {
        /* Preenchido por Web Service */
        countryAdapter = ArrayAdapter<String>(this, R.layout.spinner_item)
        countryNameSlugMap = mutableMapOf()
        countrySp.adapter = countryAdapter
        viewModel.fetchCountries().observe(
            this,
            Observer { countryList ->
                countryList.sortedBy { it.country }.forEach { countryListItem ->
                    if ( countryListItem.country.isNotEmpty()) {
                        countryAdapter.add(countryListItem.country)
                        countryNameSlugMap[countryListItem.country] = countryListItem.slug
                    }
                }
            }
        )
    }

    private fun informationAdapterInit() {
        val informationList = arrayListOf<String>()
        Information.values().forEach { informationList.add(it.desc) }

        infoSp.adapter = ArrayAdapter(this, R.layout.spinner_item, informationList)
        infoSp.onItemSelectedListener = object: AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) { }

            // A nova versão dos serviços alterou a forma como dispomos os dados
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                when (position) {
                    Information.DAY_ONE.ordinal -> {
                        viewModeTv.visibility = View.VISIBLE
                        viewModeRg.visibility = View.VISIBLE
                    }
                    Information.BY_COUNTRY.ordinal -> {
                        viewModeTv.visibility = View.GONE
                        viewModeRg.visibility = View.GONE
                    }
                }
            }
        }
    }

    private fun statusAdapterInit() {
        val statusList = arrayListOf<String>()
        Status.values().forEach { statusList.add(it.desc) }

        statusSp.adapter = ArrayAdapter(this, R.layout.spinner_item, statusList)
    }

    private fun getTypeByDesc(desc: String) : String {
        return when(desc) {
            Status.CONFIRMED.desc -> Status.CONFIRMED.type
            Status.DEATHS.desc -> Status.DEATHS.type
            Status.RECOVERED.desc -> Status.RECOVERED.type
            else -> "Status Inválido" }
    }

    private fun fetchDayOne() {
        val countrySlug = countryNameSlugMap[countrySp.selectedItem.toString()]!!

        viewModel.fetchDayOne(countrySlug, getTypeByDesc(statusSp.selectedItem.toString())).observe(
            this,
            Observer { casesList ->
                progressBar.visibility = View.GONE
                if (viewModeTextRb.isChecked) {
                    /* Modo texto */
                    modoGrafico(ligado = false)
                    resultTv.text = casesListToString(casesList)
                }
                else {
                    /* Modo gráfico */
                    modoGrafico(ligado = true)
                    resultGv.removeAllSeries()
                    resultGv.gridLabelRenderer.resetStyles()

                    /* Preparando pontos */
                    val pointsArrayList = arrayListOf<DataPoint>()
                    casesList.forEach {
                        val date = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(it.date.substring(0,10))
                        val point = DataPoint(date, it.cases.toDouble())
                        pointsArrayList.add(point)
                    }
                    val pointsSeries = LineGraphSeries(pointsArrayList.toTypedArray())
                    resultGv.addSeries(pointsSeries)

                    /* Formatando gráfico */
                    resultGv.gridLabelRenderer.setHumanRounding(false)
                    resultGv.gridLabelRenderer.labelFormatter = DateAsXAxisLabelFormatter(this)

                    resultGv.gridLabelRenderer.numHorizontalLabels = 4
                    if (pointsArrayList.isNotEmpty()) {
                        val primeiraData = Date(pointsArrayList.first().x.toLong())
                        val ultimaData = Date(pointsArrayList.last().x.toLong())
                        resultGv.viewport.setMinX(primeiraData.time.toDouble())
                        resultGv.viewport.setMaxX(ultimaData.time.toDouble())
                        resultGv.viewport.isXAxisBoundsManual = true

                        resultGv.gridLabelRenderer.numVerticalLabels = 4
                        resultGv.viewport.setMinY(pointsArrayList.first().y)
                        resultGv.viewport.setMaxY(pointsArrayList.last().y)
                        resultGv.viewport.isYAxisBoundsManual = true
                    } else {
                        Toast.makeText(this,"Nenhum resultado encontrado!", Toast.LENGTH_LONG).show()
                    }
                }
            }
        )
    }

    private fun fetchByCountry() {
        val countrySlug = countryNameSlugMap[countrySp.selectedItem.toString()]!!

        modoGrafico(ligado = false)
        viewModel.fetchByCountry(countrySlug, getTypeByDesc(statusSp.selectedItem.toString())).observe(
            this,
            Observer { casesList ->
                progressBar.visibility = View.GONE
                resultTv.text = casesListToString(casesList)
            }
        )
    }

    private fun modoGrafico(ligado: Boolean) {
        if (ligado) {
            resultTv.visibility = View.GONE
            resultGv.visibility = View.VISIBLE
        }
        else {
            resultTv.visibility = View.VISIBLE
            resultGv.visibility = View.GONE
        }
    }

    private inline fun <reified  T: ArrayList<*>> casesListToString(responseList: T): String {
        val resultSb = StringBuffer()

        if (responseList.isEmpty()) {
            Toast.makeText(this,"Nenhum resultado encontrado!", Toast.LENGTH_LONG).show()
        }
        // Usando class.java para não ter que adicionar biblioteca de reflexão Kotlin
        responseList.forEach {
            when(T::class.java) {
                DayOneResponseList::class.java -> {
                    with (it as DayOneResponseListItem) {
                        resultSb.append("Casos: ${this.cases}\n")
                        val date = SimpleDateFormat("yyyy-MM-dd").parse(this.date.substring(0,10))
                        val formattedDate = SimpleDateFormat("dd/MM/yyyy").format(date)
                        resultSb.append("Data: ${formattedDate}\n\n")
                    }
                }
                ByCountryResponseList::class.java -> {
                    with (it as ByCountryResponseListItem) {
                        this.province.takeIf { !this.province.isNullOrEmpty() }?.let { province ->
                            resultSb.append("Estado/Província: ${province}\n")
                        }
                        this.city.takeIf { !this.city.isNullOrEmpty() }?.let { city ->
                            resultSb.append("Cidade: ${city}\n")
                        }

                        val date = SimpleDateFormat("yyyy-MM-dd").parse(this.date.substring(0,10))
                        val formattedDate = SimpleDateFormat("dd/MM/yyyy").format(date)
                        resultSb.append("Casos: ${this.cases}\n")
                        resultSb.append("Data: ${formattedDate}\n\n")
                    }
                }
            }
        }

        return resultSb.toString()
    }
}
