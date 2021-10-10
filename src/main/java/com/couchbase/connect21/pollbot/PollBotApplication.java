package com.couchbase.connect21.pollbot;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.javafaker.Faker;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

@SpringBootApplication
public class PollBotApplication implements CommandLineRunner {

	public static final String URL = "--url";
	public static final String LANGUAGES = "--languages";
	public static final String USERS = "--users";
	public static final String ANSWERS = "--answers";
	public static final String RESET = "--reset";
	public static final String SKIP_USERS = "--skipUsers";

	public static void main(String[] args) {
		SpringApplication.run(PollBotApplication.class, args);
	}


	@Override
	public void run(String... args) throws Exception {
		if( args.length == 0 ||  "--help".equals( args[0]) ) {
			printHelp();
			return;
		}

		Map<String, String> params = parseParams(args);
		if(Boolean.parseBoolean(params.get(RESET))) {
			cleanDatabase(params.get(URL));
		}

		Map<String, List<String>> users = null;

		if(!Boolean.parseBoolean(params.get(SKIP_USERS))) {
			System.out.println("=====Creating users");
			users = createUsers(params);
			System.out.println("======================================");
			System.out.println("=====All users have been created======");
			System.out.println("======================================");
		} else {
			throw new IllegalArgumentException("Not Implemented yet");
		}

		System.out.println("======================================");
		System.out.println("=====Waiting to answer questions======");
		System.out.println("======================================");
		answerQuestions(params, users);
	}

	private void answerQuestions(Map<String, String> params, Map<String, List<String>> users ) throws Exception{
		String lastQuestion = null;

		while (true) {

			Poll poll = getActivePoll(params);
			if( poll != null && !poll.getId().equals(lastQuestion) ) {
				System.out.println("======================================");
				System.out.println("=====Answering New Question with Threads======");
				System.out.println("======================================");
				lastQuestion = poll.getId();
				answerActivePoll(users, params, poll);
			} else {
				System.out.println("Waiting for question to be unlocked.");
				Thread.sleep(2000);
			}
		}
	}

	private void answerActivePoll(Map<String, List<String>> users, Map<String, String> params, Poll poll) {

		String[] answers = params.get(ANSWERS).split(",");
		String[] langs = params.get(LANGUAGES).split(",");
		ExecutorService executor = Executors.newFixedThreadPool(10);

		List<Poll.Answer> correct = poll.getAnswers().stream()
				.filter(e-> e.isCorrect()).collect(Collectors.toList());

		List<Poll.Answer> wrong = poll.getAnswers().stream()
				.filter(e-> !e.isCorrect()).collect(Collectors.toList());

		for(Map.Entry<String, List<String>> entry: users.entrySet()) {
			int percentRight = Integer.parseInt(answers[Arrays.asList(langs).indexOf(entry.getKey())]);
			int totalUserAnswers =  (int)  ((entry.getValue().size() * percentRight)/100);

			executor.submit(() -> answerPool(entry.getKey(), params.get(URL), users.get(entry.getKey()), totalUserAnswers, correct,  wrong));

		}
	}

	private void answerPool(String lang, String url, List<String> userIds, int totalCorrect, List<Poll.Answer> correct, List<Poll.Answer> wrong) {

		for(int i = 0; i<userIds.size();i++){
			String answer = "";

			//answer correct first
			if(totalCorrect > i){
				if(correct.size()> 0) {
					answer = correct.get(0).getText();
				} else {
					answer = wrong.get(0).getText();
				}
			} else {
				if(wrong.size() > 0) {
					answer = wrong.get(random(0, wrong.size() - 1)).getText();
				} else {
					answer = correct.get(random(0, correct.size() - 1)).getText();
				}
			}

			try {
				HttpClient client = HttpClient.newHttpClient();
				HttpRequest request = HttpRequest.newBuilder()
						.uri(URI.create(url+ "/demo/answerPool?userId="+userIds.get(i)+"&answer="+ URLEncoder.encode(answer, StandardCharsets.UTF_8.toString())))
						.headers("Content-Type", "application/json;charset=UTF-8")
						.POST(HttpRequest.BodyPublishers.ofString("{}"))
						.build();
				client.send(request,
						HttpResponse.BodyHandlers.ofString());

			} catch (Exception e) {
				System.err.println("Could not get the current poll");
			}
		}
		System.out.println("============= all users from "+lang+" has finished");
	}

	int random(int max, int min){
		int range = max - min + 1;
		// Math.random() function will return a random no between [0.0,1.0).
		int res = (int) ( Math.random()*range)+min;
		return res;
	}

	private Poll getActivePoll(Map<String, String> params) {
		try {
			HttpClient client = HttpClient.newHttpClient();
			HttpRequest request = HttpRequest.newBuilder()
					.uri(URI.create(params.get(URL) + "/demo/activePool"))
					.GET()
					.build();
			HttpResponse<String> response = client.send(request,
					HttpResponse.BodyHandlers.ofString());
			ObjectMapper mapper = new ObjectMapper();
			return mapper.readValue(response.body(), Poll.class);
		} catch (Exception e) {
			System.err.println("Could not get the current poll");
		}
		return null;
	}

	private Map<String, List<String>> createUsers(Map<String, String> params){

		Faker faker = new Faker();
		String[] users = params.get(USERS).split(",");
		String[] langs = params.get(LANGUAGES).split(",");
		Map<String, List<String>> usersList = new HashMap<>();

		for(int i=0; i< users.length; i++) {
			int userCount = Integer.parseInt(users[i]);
			String targetLang = langs[i];
			usersList.put(targetLang, new ArrayList<>());

			for(int j=0; j<userCount; j++) {

				try {
					String name = faker.name().username();
					String email = name + "@gmail.com";
					HttpClient client = HttpClient.newHttpClient();
					HttpRequest request = HttpRequest.newBuilder()
							.uri(URI.create(params.get(URL) + "/demo/addUser"))
							.headers("Content-Type", "application/json;charset=UTF-8")
							.POST(HttpRequest.BodyPublishers.ofString("{\"email\": \"" + email + "\",\n" +
									"    \"name\": \"" + name + "\",\n" +
									"    \"team\": \"" + targetLang + "\"}"))
							.build();
					HttpResponse<String> response = client.send(request,
							HttpResponse.BodyHandlers.ofString());

					JSONObject jsonObj = new JSONObject(response.body());

					if (jsonObj.has("id")) {
						usersList.get(targetLang).add(jsonObj.getString("id"));
					} else {
						System.err.println("User with team" + targetLang + " could not be created");
					}

				} catch (Exception e) {
					System.err.println("User with team" + targetLang + " could not be created");
				}
			}
		}

		return usersList;
	}

	private void cleanDatabase(String url) throws Exception {

		try {
			System.out.println("====== The database will be cleaned");
			HttpClient client = HttpClient.newHttpClient();
			HttpRequest request = HttpRequest.newBuilder()
					.uri(new URI(url+"/demo/reset"))
					.GET()
					.build();
			client.send(request,
					HttpResponse.BodyHandlers.ofString());
			System.out.println("====== The database has been cleaned");
		} catch (Exception e) {
			throw e;
		}
	}




	private Map<String, String> parseParams(String... args) {

		int i = 0;
		Map<String, String> params = new HashMap<>();
		while (i < args.length) {

			if(URL.equals(args[i])) {
				params.put(URL, args[(i+1)]);
				i++;
				i++;
				continue;
			} else if(LANGUAGES.equals(args[i])) {
				params.put(LANGUAGES, args[i+1]);
				i++;i++;

			} else if(USERS.equals(args[i])) {
				params.put(USERS, args[i+1]);
				i++;i++;

			} else if(ANSWERS.equals(args[i])) {
				params.put(ANSWERS, args[i+1]);
				i++;i++;
			} else if(RESET.equals(args[i])) {
				params.put(RESET, args[i+1]);
				i++;i++;
			}
		}

		if(!params.containsKey(URL)) {
			params.put(URL, "http://localhost:8080");
		}

		int lang = 0;
		if(!params.containsKey(LANGUAGES)) {
			params.put(LANGUAGES, "Python,Javascript,Java,C++,C#,GO" );
		}

		lang = params.get(LANGUAGES).split(",").length;
		if(lang < 2) {
			throw new IllegalStateException("please specify at least 2 languages");
		}

		if(!params.containsKey(USERS)) {
			params.put(USERS, "11,35,30,5,13,4" );
		}
		if(params.get(USERS).split(",").length != lang){
			throw new IllegalStateException("Array of users doesn't has the same size as the array of languages");
		}

		if(!params.containsKey(ANSWERS)) {
			params.put(ANSWERS, "50,50,50,50,50,50" );
		}
		if(params.get(ANSWERS).split(",").length != lang){
			throw new IllegalStateException("Array of answers doesn't has the same size as the array of languages");
		}

		if(!params.containsKey(RESET)) {
			params.put(RESET, "false" );
		}

		if(!params.containsKey(SKIP_USERS)) {
			params.put(SKIP_USERS, "false" );
		}


		return params;
	}

	private void printHelp() {
		System.out.println("--url			String				target BACKEND url (default:localhost:8080)");
		System.out.println("--languages     String with comma	languages separated by comma (default: Python,Javascript,Java,C++,C#,GO )");
		System.out.println("--users     	int with commas		the number of users per language (default: 11,35,30,5,13,4 )");
		System.out.println("--answers		int with commas		the number of correct answers per team (default: 50,50,50,50,50,50 )");
		System.out.println("--reset			boolean				resets the database (default:false)");
		System.out.println("--skipUsers		boolean				skipUserCreation (default:false)");
	}
}
