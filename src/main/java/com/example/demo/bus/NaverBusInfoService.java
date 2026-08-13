package com.example.demo.bus;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.PageLoadStrategy;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.stereotype.Service;

@Service
public class NaverBusInfoService {

	private static final int MAX_LINES = 100;

	public BusRouteInfo fetchRouteInfo(String busNumber, String region) {
		String searchBusNumber = busNumber.replaceAll("\\([^)]*\\)", "").trim();
		String query = region + " " + searchBusNumber + " 버스";
		String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8).replace("+", "%20");
		URI url = URI.create("https://map.naver.com/p/search/" + encodedQuery);

		ChromeOptions options = new ChromeOptions();
		options.setPageLoadStrategy(PageLoadStrategy.EAGER);
		options.addArguments("--headless=new");
		options.addArguments("--disable-gpu");
		options.addArguments("--no-sandbox");
		options.addArguments("--window-size=1280,900");

		WebDriver driver = new ChromeDriver(options);
		try {
			driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(20));
			driver.get(url.toString());

			WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(8));
			wait.until(currentDriver -> "complete".equals(
				((JavascriptExecutor) currentDriver).executeScript("return document.readyState")));
				try {
					wait.until(currentDriver -> hasRouteLines(readBestLines(currentDriver, searchBusNumber, region), searchBusNumber, region));
				} catch (TimeoutException ex) {
					// Keep the default document text when Naver does not expose a populated iframe.
				}

				List<String> searchLines = readBestLines(driver, searchBusNumber, region);
				try {
					clickFirstNaverBusResult(driver, searchBusNumber, region);
					WebDriverWait detailWait = new WebDriverWait(driver, Duration.ofSeconds(6));
					detailWait.until(currentDriver -> scoreLines(readBestLines(currentDriver, searchBusNumber, region), searchBusNumber, region) > scoreLines(searchLines, searchBusNumber, region));
					List<String> detailLines = readBestLines(driver, searchBusNumber, region);
					return parse(url.toString(), driver.getTitle(), mergeLines(searchLines, detailLines), searchBusNumber, region);
				} catch (TimeoutException | IllegalStateException ex) {
					return parse(url.toString(), driver.getTitle(), searchLines, searchBusNumber, region);
				}
			} finally {
				driver.quit();
			}
	}

	private BusRouteInfo parse(String url, String title, List<String> lines, String busNumber, String region) {
		String routeLine = lines.stream()
			.filter(line -> line.contains(region))
			.filter(line -> Pattern.compile("(^|\\s)" + Pattern.quote(busNumber) + "(\\s|$)").matcher(line).find())
			.findFirst()
			.orElse("");
		String directionLine = lines.stream()
				.filter(line -> line.contains(" 에서 "))
				.findFirst()
				.orElse("");

		String busType = busTypeNearRoute(lines, busNumber);
		if (!routeLine.isBlank()) {
			String[] parts = routeLine.split("\\s+");
			if (parts.length > 0) {
				busType = parts[0];
			}
		}

		String origin = "";
		String destination = "";
		if (!directionLine.isBlank()) {
			String[] parts = directionLine.split("\\s+에서\\s+", 2);
			origin = parts[0];
			if (parts.length > 1) {
				destination = parts[1];
			}
		} else {
			String naverDirectionLine = lines.stream()
				.filter(line -> line.contains("↔"))
				.filter(line -> !line.contains("검색결과"))
				.findFirst()
				.orElse("");
			if (!naverDirectionLine.isBlank()) {
				String[] parts = naverDirectionLine.split("\\s*↔\\s*", 2);
				origin = parts[0];
				if (parts.length > 1) {
					destination = parts[1];
				}
			}
		}

		String firstBus = firstBusTime(lines);
		String lastBus = lastBusTime(lines);
		String saturdayFirstBus = firstBusTimeInSection(lines, "토요일", "주말");
		String saturdayLastBus = lastBusTimeInSection(lines, "토요일", "주말");
		String sundayFirstBus = firstBusTimeInSection(lines, "일요일", "주말");
		String sundayLastBus = lastBusTimeInSection(lines, "일요일", "주말");
		String holidayFirstBus = firstBusTimeInSection(lines, "휴일", "공휴일", "주말");
		String holidayLastBus = lastBusTimeInSection(lines, "휴일", "공휴일", "주말");
		if (hasWeekendInfo(lines)) {
			saturdayFirstBus = valueOrFallback(saturdayFirstBus, firstBus);
			saturdayLastBus = valueOrFallback(saturdayLastBus, lastBus);
			sundayFirstBus = valueOrFallback(sundayFirstBus, firstBus);
			sundayLastBus = valueOrFallback(sundayLastBus, lastBus);
			holidayFirstBus = valueOrFallback(holidayFirstBus, firstBus);
			holidayLastBus = valueOrFallback(holidayLastBus, lastBus);
		}

		String weekdayHeadway = formatHeadway(weekdayHeadway(lines));
		String everyDayHeadway = formatHeadway(headwayInSection(lines, "매일"));
		String saturdayHeadway = formatHeadway(headwayInSection(lines, "토요일", "주말"));
		String sundayHeadway = formatHeadway(headwayInSection(lines, "일요일", "주말"));
		String holidayHeadway = formatHeadway(headwayInSection(lines, "휴일", "공휴일", "주말"));
		weekdayHeadway = valueOrFallback(weekdayHeadway, everyDayHeadway);
		saturdayHeadway = valueOrFallback(saturdayHeadway, everyDayHeadway);
		sundayHeadway = valueOrFallback(sundayHeadway, everyDayHeadway);
		holidayHeadway = valueOrFallback(holidayHeadway, everyDayHeadway);

		return new BusRouteInfo(
			url,
			title,
			busType,
			origin,
			destination,
			firstBus,
			lastBus,
			saturdayFirstBus,
			saturdayLastBus,
			sundayFirstBus,
			sundayLastBus,
			holidayFirstBus,
			holidayLastBus,
			weekdayHeadway,
			runsAfterLabel(lines),
			saturdayHeadway,
			"",
			sundayHeadway,
			"",
			holidayHeadway,
			"",
			lines
		);
	}

	private String busTypeNearRoute(List<String> lines, String busNumber) {
		for (int i = 0; i < lines.size(); i++) {
			if (!lines.get(i).equals(busNumber)) {
				continue;
			}
			for (int j = i - 1; j >= 0 && j >= i - 3; j--) {
				String line = lines.get(j);
				if (line.contains("마을") || line.contains("일반") || line.contains("직행") || line.contains("좌석")
					|| line.contains("간선") || line.contains("지선") || line.contains("광역")) {
					return line;
				}
			}
		}
		return "";
	}

	private boolean hasWeekendInfo(List<String> lines) {
		return lines.stream().anyMatch(line -> line.contains("주말") || line.contains("토요일")
			|| line.contains("일요일") || line.contains("휴일") || line.contains("공휴일"));
	}

	private String valueOrFallback(String value, String fallback) {
		return value == null || value.isBlank() ? fallback : value;
	}

	private List<String> readBodyLines(WebDriver driver) {
		WebElement body = driver.findElement(By.tagName("body"));
		return Arrays.stream(body.getText().split("\\R"))
			.map(String::trim)
			.filter(line -> !line.isEmpty())
			.limit(MAX_LINES)
			.toList();
	}

	private void clickFirstNaverBusResult(WebDriver driver, String busNumber, String region) {
		driver.switchTo().defaultContent();
		try {
			driver.switchTo().frame("searchIframe");
		} catch (RuntimeException ex) {
			driver.switchTo().defaultContent();
		}

		List<By> candidates = List.of(
			By.xpath("//*[normalize-space(.)='" + busNumber + "']"),
			By.xpath("//*[contains(normalize-space(.), '" + busNumber + "') and contains(normalize-space(.), '" + region + "')]"),
			By.xpath("//*[contains(normalize-space(.), '" + busNumber + "')]")
		);

		for (By candidate : candidates) {
			List<WebElement> elements = driver.findElements(candidate).stream()
				.filter(WebElement::isDisplayed)
				.sorted(Comparator.comparingDouble(this::elementArea))
				.toList();
			for (WebElement element : elements) {
				((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block: 'center'});", element);
				((JavascriptExecutor) driver).executeScript("arguments[0].click();", element);
				driver.switchTo().defaultContent();
				return;
			}
		}
		driver.switchTo().defaultContent();
		throw new IllegalStateException("No Naver bus result was clickable.");
	}

	private double elementArea(WebElement element) {
		return element.getRect().getWidth() * element.getRect().getHeight();
	}

	private List<String> mergeLines(List<String> first, List<String> second) {
		return java.util.stream.Stream.concat(first.stream(), second.stream())
			.distinct()
			.limit(MAX_LINES)
			.toList();
	}

	private List<String> readBestLines(WebDriver driver, String busNumber, String region) {
		List<String> defaultLines = readBodyLines(driver);
		List<String> bestLines = defaultLines;
		int bestScore = scoreLines(defaultLines, busNumber, region);
		for (String frameName : List.of("searchIframe", "entryIframe")) {
			try {
				driver.switchTo().defaultContent();
				driver.switchTo().frame(frameName);
				List<String> frameLines = readBodyLines(driver);
				int frameScore = scoreLines(frameLines, busNumber, region);
				if (frameScore > bestScore) {
					bestLines = frameLines;
					bestScore = frameScore;
				}
			} catch (RuntimeException ex) {
				// Try the next frame; Naver changes iframe availability by page state.
			}
		}
		driver.switchTo().defaultContent();
		return bestLines;
	}

	private boolean hasRouteLines(List<String> lines, String busNumber, String region) {
		return scoreLines(lines, busNumber, region) >= 3;
	}

	private int scoreLines(List<String> lines, String busNumber, String region) {
		int score = 0;
		for (String line : lines) {
			if (region != null && line.contains(region)) {
				score++;
			}
			if (busNumber != null && Pattern.compile("(^|\\s)" + Pattern.quote(busNumber) + "(\\s|$)").matcher(line).find()) {
				score++;
			}
			if (line.contains("첫차") || line.contains("막차") || line.contains("배차")) {
				score += 2;
			}
			if (line.contains("토요일") || line.contains("일요일") || line.contains("공휴일") || line.contains("휴일")) {
				score += 2;
			}
			if (line.contains(" 에서 ")) {
				score++;
			}
			if (line.contains("↔")) {
				score++;
			}
		}
		return score;
	}

	private String valueByPattern(List<String> lines, String regex) {
		Pattern pattern = Pattern.compile(regex);
		for (String line : lines) {
			Matcher matcher = pattern.matcher(line);
			if (matcher.find()) {
				return matcher.group(1).trim();
			}
		}
		return "";
	}

	private String formatHeadway(String headway) {
		if (headway == null || headway.isBlank()) {
			return "";
		}

		String normalized = headway.replace("~", "-").replaceAll("\\s+", "");
		Matcher matcher = Pattern.compile("([0-9]+)-([0-9]+)분?").matcher(normalized);
		if (matcher.find()) {
			return matcher.group(1) + "-" + matcher.group(2) + " 분";
		}

		matcher = Pattern.compile("([0-9]+)분").matcher(normalized);
		if (matcher.find()) {
			return matcher.group(1) + "-" + matcher.group(1) + " 분";
		}

		return headway.trim();
	}

	private String timeAfterLabel(List<String> lines, String label) {
		String value = valueByPattern(lines, label + "\\s*([0-9]{1,2}:[0-9]{2})");
		if (!value.isBlank()) {
			return value;
		}

		return valueNearLabel(lines, label, "[0-9]{1,2}:[0-9]{2}");
	}

	private String firstBusTime(List<String> lines) {
		String value = timeAfterLabel(lines, "첫차");
		if (!value.isBlank()) {
			return value;
		}
		return valueByPattern(lines, "운행정보\\s*([0-9]{1,2}:[0-9]{2})\\s*(?:~|-)");
	}

	private String lastBusTime(List<String> lines) {
		String value = timeAfterLabel(lines, "막차");
		if (!value.isBlank()) {
			return value;
		}
		return valueByPattern(lines, "운행정보\\s*[0-9]{1,2}:[0-9]{2}\\s*(?:~|-)\\s*([0-9]{1,2}:[0-9]{2})");
	}

	private String firstBusTimeInSection(List<String> lines, String... labels) {
		String value = timeInSection(lines, "첫차", labels);
		if (!value.isBlank()) {
			return value;
		}
		return timeRangeInSection(lines, true, labels);
	}

	private String lastBusTimeInSection(List<String> lines, String... labels) {
		String value = timeInSection(lines, "막차", labels);
		if (!value.isBlank()) {
			return value;
		}
		return timeRangeInSection(lines, false, labels);
	}

	private String timeRangeInSection(List<String> lines, boolean first, String... labels) {
		for (String label : labels) {
			Pattern pattern = Pattern.compile(label + "\\s*([0-9]{1,2}:[0-9]{2})\\s*(?:~|-)\\s*([0-9]{1,2}:[0-9]{2})");
			for (String line : lines) {
				Matcher matcher = pattern.matcher(line);
				if (matcher.find()) {
					return matcher.group(first ? 1 : 2).trim();
				}
			}
		}
		return "";
	}

	private String timeInSection(List<String> lines, String timeLabel, String... sectionLabels) {
		for (String sectionLabel : sectionLabels) {
			for (int i = 0; i < lines.size(); i++) {
				if (!lines.get(i).contains(sectionLabel)) {
					continue;
				}
				for (int j = i; j < lines.size() && j <= i + 8; j++) {
					String line = lines.get(j);
					Matcher labeledMatcher = Pattern.compile(timeLabel + "\\s*([0-9]{1,2}:[0-9]{2})").matcher(line);
					if (labeledMatcher.find()) {
						return labeledMatcher.group(1).trim();
					}
					if (line.contains(timeLabel)) {
						String value = valueNearLabel(lines.subList(j, Math.min(lines.size(), j + 4)), timeLabel, "[0-9]{1,2}:[0-9]{2}");
						if (!value.isBlank()) {
							return value;
						}
					}
				}
			}
		}
		return "";
	}

	private String weekdayHeadway(List<String> lines) {
		String value = valueByPattern(lines, "평일\\s*([0-9]+\\s*(?:~|-)\\s*[0-9]+\\s*분?|[0-9]+\\s*분)");
		if (!value.isBlank()) {
			return value;
		}
		return headwayAfterLabel(lines);
	}

	private String headwayInSection(List<String> lines, String... labels) {
		for (String label : labels) {
			String value = valueByPattern(lines, label + "\\s*([0-9]+\\s*(?:~|-)\\s*[0-9]+\\s*분?|[0-9]+\\s*분)");
			if (!value.isBlank()) {
				return value;
			}
		}
		return "";
	}

	private String headwayAfterLabel(List<String> lines) {
		String value = valueByPattern(lines, "배차간격\\s*([^현재]+)");
		if (!value.isBlank()) {
			return value;
		}

		return valueNearLabel(lines, "배차간격", "[0-9]+\\s*(?:~|-)\\s*[0-9]+\\s*분?|[0-9]+\\s*분");
	}

	private String runsAfterLabel(List<String> lines) {
		String value = valueByPattern(lines, "현재\\s*([^\\s]+대\\s*운행\\s*중)");
		if (!value.isBlank()) {
			return value;
		}

		value = valueNearLabel(lines, "현재", "[0-9]+\\s*대\\s*운행\\s*중");
		if (!value.isBlank()) {
			return value;
		}

		return valueByPattern(lines, "([0-9]+\\s*대\\s*운행\\s*중)");
	}

	private String valueNearLabel(List<String> lines, String label, String valueRegex) {
		Pattern valuePattern = Pattern.compile("(" + valueRegex + ")");
		for (int i = 0; i < lines.size(); i++) {
			if (!lines.get(i).contains(label)) {
				continue;
			}
			for (int j = i; j < lines.size() && j <= i + 3; j++) {
				Matcher matcher = valuePattern.matcher(lines.get(j));
				if (matcher.find()) {
					return matcher.group(1).trim();
				}
			}
		}
		return "";
	}
}
