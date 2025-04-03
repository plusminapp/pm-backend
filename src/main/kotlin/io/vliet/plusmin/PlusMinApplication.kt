package io.vliet.plusmin

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableScheduling
class PlusMinApplication

fun main(args: Array<String>) {
	runApplication<PlusMinApplication>(*args)
}
