package com.yodawife.easyll;

import com.yodawife.easyll.config.DictionaryProperties;
import com.yodawife.easyll.config.MatchGameProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableConfigurationProperties({MatchGameProperties.class, DictionaryProperties.class})
@EnableScheduling
public class EasyllApplication {

	public static void main(String[] args) {
		SpringApplication.run(EasyllApplication.class, args);
	}

}
