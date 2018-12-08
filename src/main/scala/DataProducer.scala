package main.scala

import java.util
import java.util.Properties

import org.apache.commons.math3.distribution.NormalDistribution
import org.apache.http.NameValuePair

import scala.util.Random
import scala.util.parsing.json.JSON
import org.apache.http.client.HttpClient
import org.apache.http.client.entity.UrlEncodedFormEntity
import org.apache.http.client.methods.HttpPost
import org.apache.http.impl.client.HttpClients
import org.apache.http.message.BasicNameValuePair
import org.apache.kafka.clients.producer.{KafkaProducer, ProducerRecord}

object DataProducer extends App {

  case class DataModel(date: String,
                       value: Double,
                       place: String)

  final val globalIdAddress = "192.168.0.108"
  final val amountOfPlaces = 20
  final val listOfPlaces: Seq[String] = for (i <- 0 until amountOfPlaces) yield "Osiedle" + i
  final def getUrlAddress(dataType: String) = s"http://$globalIdAddress:8000/api/$dataType/newest/?format=json"
  final def postUrlAddress(dataType: String) = s"http://$globalIdAddress:8000/api/$dataType/"
  final val kafkaAddress = s"$globalIdAddress:port,$globalIdAddress:port"

  final val temperature = "temperature"
  final val humidity = "humidity"
  final val electricity = "electricity"
  final val water = "water"
  final val pollution = "pollution"
  final val listOfAllTypes = List(temperature,humidity,electricity,water,pollution)

  final def kafkaProperties: Properties = {
    val props = new Properties()
    props.put("bootstrap.servers", "localhost:9092,localhost:9093,localhost:9094")
    props.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer")
    props.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer")
    props
  }

  val kafkaProducer = new KafkaProducer[String,String](kafkaProperties)

  def sendKafkaMessage(record: DataModel, topic: String) = {
    kafkaProducer.send(new ProducerRecord[String,String](topic, "1", record.toString))
  }

  def get(url: String) = scala.io.Source.fromURL(url).mkString
  def post(url: String, data: DataModel) = {
    val client: HttpClient = HttpClients.createDefault()
    val postRequest: HttpPost = new HttpPost(url)

    // Request parameters and other properties.
    val params: util.ArrayList[NameValuePair] = new util.ArrayList[NameValuePair](2)
    params.add(new BasicNameValuePair("value", data.value.toString))
    params.add(new BasicNameValuePair("date", data.date))
    params.add(new BasicNameValuePair("place", data.place))
    postRequest.setEntity(new UrlEncodedFormEntity(params, "UTF-8"))

    client.execute(postRequest)
  }
  val randInstance = new Random(System.currentTimeMillis())

  listOfAllTypes.map(dataType => {
    val getUrlString = getUrlAddress(dataType)
    val postUrlString = postUrlAddress(dataType)
    val valueFromGet = JSON.parseFull(get(getUrlString).toString).get.asInstanceOf[Map[String, Any]]("value").toString.toDouble
    for (i <- 0 until 5)
      yield {
        val valueFromNormalDistribution = new NormalDistribution(valueFromGet,1.0)
        val dataModel = DataModel(
          date = System.currentTimeMillis.toString,
          value = BigDecimal(valueFromNormalDistribution.sample()).setScale(1,BigDecimal.RoundingMode.HALF_UP).doubleValue(),
            place = {
            val randomIndex = randInstance.nextInt(amountOfPlaces)
            listOfPlaces(randomIndex)
          })
        //post(postUrlString,dataModel)
        sendKafkaMessage(dataModel,temperature)
      }
  })
}
