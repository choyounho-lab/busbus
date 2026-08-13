package com.example.demo.bus;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;
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
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.stereotype.Service;

@Service
public class SeleniumBusInfoService {

	private static final int MAX_LINES = 160;
	private static final int MAX_BUS_LINES = 40;

	public BusInfoSnapshot fetchKakaoBusInfo(String query) {
		String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8).replace("+", "%20");
		URI url = URI.create("https://map.kakao.com/?q=" + encodedQuery);
		BusInfoSnapshot snapshot = fetchSnapshot(url, lines -> lines.contains("버스번호") || lines.stream()
			.anyMatch(line -> line.contains("검색결과")));

		List<String> busLines = extractKakaoBusSection(snapshot.lines());
		if (busLines.isEmpty()) {
			busLines = snapshot.lines().stream()
			.filter(this::looksLikeBusInfo)
			.limit(MAX_BUS_LINES)
			.toList();
		}

		if (busLines.isEmpty()) {
			return snapshot;
		}

		return new BusInfoSnapshot(snapshot.url(), snapshot.title(), busLines);
	}

	public BusRouteInfo fetchKakaoBusRouteInfo(String query) {
		return fetchKakaoBusRouteInfo(query, null, null);
	}

	public BusRouteInfo fetchKakaoBusRouteInfo(String busNumber, String region) {
		String searchBusNumber = normalizeBusNumberForSearch(busNumber);
		String query = region + " " + searchBusNumber + " 버스";
		return fetchKakaoBusRouteInfo(query, searchBusNumber, region);
	}

	private BusRouteInfo fetchKakaoBusRouteInfo(String query, String busNumber, String region) {
		String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8).replace("+", "%20");
		URI url = URI.create("https://map.kakao.com/?q=" + encodedQuery);
		validateHttpUrl(url);

		ChromeOptions options = createChromeOptions();
		WebDriver driver = new ChromeDriver(options);
		try {
			driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(20));
			driver.get(url.toString());

			WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
			wait.until(currentDriver -> readBodyLines(currentDriver).contains("버스번호"));

			List<String> searchLines = extractKakaoBusSection(readBodyLines(driver));
			try {
				clickFirstKakaoBusResult(driver, busNumber, region);
					WebDriverWait detailWait = new WebDriverWait(driver, Duration.ofSeconds(4));
					detailWait.until(currentDriver -> readBodyLines(currentDriver).stream()
						.anyMatch(line -> line.contains("첫차") || line.contains("막차") || line.contains("배차")));

					clickOptionalText(driver, "더보기");
					List<String> detailLines = readBodyLines(driver);
					return parseRouteInfo(url.toString(), driver.getTitle(), detailLines, busNumber, region);
			} catch (TimeoutException | IllegalStateException ex) {
				return parseRouteInfo(url.toString(), driver.getTitle(), searchLines, busNumber, region);
			}
		} finally {
			driver.quit();
		}
	}

	public BusInfoSnapshot fetchSnapshot(URI url) {
		return fetchSnapshot(url, lines -> lines.size() > 10);
	}

	private BusInfoSnapshot fetchSnapshot(URI url, Predicate<List<String>> loaded) {
		validateHttpUrl(url);

			ChromeOptions options = createChromeOptions();

		WebDriver driver = new ChromeDriver(options);
		try {
			driver.get(url.toString());

			WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
			WebElement body = wait.until(ExpectedConditions.presenceOfElementLocated(By.tagName("body")));
			wait.until(currentDriver -> "complete".equals(
				((JavascriptExecutor) currentDriver).executeScript("return document.readyState")));
			wait.until(currentDriver -> loaded.test(readBodyLines(currentDriver)));
			body = driver.findElement(By.tagName("body"));

			List<String> lines = readBodyLines(body);

			return new BusInfoSnapshot(url.toString(), driver.getTitle(), lines);
		} finally {
			driver.quit();
		}
	}

	private ChromeOptions createChromeOptions() {
		ChromeOptions options = new ChromeOptions();
		options.setPageLoadStrategy(PageLoadStrategy.EAGER);
		options.addArguments("--headless=new");
		options.addArguments("--disable-gpu");
		options.addArguments("--no-sandbox");
		options.addArguments("--window-size=1280,900");
		return options;
	}

	private void clickFirstKakaoBusResult(WebDriver driver, String busNumber, String region) {
		if (busNumber == null || region == null) {
			clickFirstVisible(driver, List.of(By.xpath("//*[contains(normalize-space(.), '경기') and contains(normalize-space(.), '시')]")));
			return;
		}

		List<By> candidates = List.of(
			By.xpath("//*[contains(normalize-space(.), '" + busNumber + "') and contains(normalize-space(.), '" + region + "')]"),
			By.xpath("//*[contains(normalize-space(.), '" + busNumber + " 경기') and contains(normalize-space(.), '" + region + "')]"),
			By.xpath("//*[contains(normalize-space(.), '" + busNumber + "')]")
		);

		clickFirstVisible(driver, candidates);
	}

	private void clickFirstVisible(WebDriver driver, List<By> candidates) {
		for (By candidate : candidates) {
			List<WebElement> elements = driver.findElements(candidate).stream()
				.filter(WebElement::isDisplayed)
				.sorted(Comparator.comparingDouble(this::elementArea))
				.toList();
			for (WebElement element : elements) {
				((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block: 'center'});", element);
				((JavascriptExecutor) driver).executeScript("arguments[0].click();", element);
				return;
			}
		}

		throw new IllegalStateException("No Kakao bus result was clickable.");
	}

	private double elementArea(WebElement element) {
		return element.getRect().getWidth() * element.getRect().getHeight();
	}

	private void clickOptionalText(WebDriver driver, String text) {
		List<WebElement> elements = driver.findElements(By.xpath("//*[normalize-space(.)='" + text + "']")).stream()
			.filter(WebElement::isDisplayed)
			.sorted(Comparator.comparingDouble(this::elementArea))
			.toList();
		if (elements.isEmpty()) {
			return;
		}

		((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block: 'center'});", elements.get(0));
		((JavascriptExecutor) driver).executeScript("arguments[0].click();", elements.get(0));
		try {
			Thread.sleep(700);
		} catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
		}
	}

	private List<String> readBodyLines(WebDriver driver) {
		return readBodyLines(driver.findElement(By.tagName("body")));
	}

	private List<String> readBodyLines(WebElement body) {
		return Arrays.stream(body.getText().split("\\R"))
			.map(String::trim)
			.filter(line -> !line.isEmpty())
			.limit(MAX_LINES)
			.toList();
	}

	private List<String> extractKakaoBusSection(List<String> lines) {
		int start = lines.indexOf("버스번호");
		if (start < 0) {
			return List.of();
		}

		int end = lines.subList(start, lines.size()).indexOf("버스 더보기");
		if (end < 0) {
			end = Math.min(lines.size(), start + MAX_BUS_LINES);
		} else {
			end = start + end;
		}

		return lines.subList(start, end).stream()
			.filter(line -> !"즐겨찾기".equals(line))
			.limit(MAX_BUS_LINES)
			.toList();
	}

	private BusRouteInfo parseRouteInfo(String url, String title, List<String> lines, String busNumber, String region) {
		String routeLine = firstLineMatching(lines, line -> looksLikeTargetRouteLine(line, busNumber, region));
		String directionLine = firstLineMatching(lines, line -> line.contains(" 에서 "));

		String busType = null;
		if (routeLine != null) {
			String[] parts = routeLine.split("\\s+");
			if (parts.length > 0) {
				busType = parts[0];
			}
		}

		String origin = null;
		String destination = null;
		if (directionLine != null) {
			String[] parts = directionLine.split("\\s+에서\\s+", 2);
			origin = parts[0];
			if (parts.length > 1) {
				destination = parts[1];
			}
		}

		String firstBus = timeAfterLabel(lines, "첫차");
		String lastBus = timeAfterLabel(lines, "막차");
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

	private boolean looksLikeTargetRouteLine(String line, String busNumber, String region) {
		if (region != null && !line.contains(region)) {
			return false;
		}
		if (busNumber == null) {
			return line.contains("경기");
		}

		return Pattern.compile("(^|\\s)" + Pattern.quote(busNumber) + "(\\s|$)").matcher(line).find();
	}

	private boolean hasWeekendInfo(List<String> lines) {
		return lines.stream().anyMatch(line -> line.contains("주말") || line.contains("토요일")
			|| line.contains("일요일") || line.contains("휴일") || line.contains("공휴일"));
	}

	private String valueOrFallback(String value, String fallback) {
		return value == null || value.isBlank() ? fallback : value;
	}

	private String normalizeBusNumberForSearch(String busNumber) {
		return busNumber.replaceAll("\\([^)]*\\)", "").trim();
	}

	private String formatHeadway(String headway) {
		if (headway == null) {
			return null;
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
		if (value != null && !value.isBlank()) {
			return value;
		}

		return valueNearLabel(lines, label, "[0-9]{1,2}:[0-9]{2}");
	}

	private String headwayAfterLabel(List<String> lines) {
		String value = valueByPattern(lines, "배차간격\\s*([^현재]+)");
		if (value != null && !value.isBlank()) {
			return value;
		}

		return valueNearLabel(lines, "배차간격", "[0-9]+\\s*(?:~|-)\\s*[0-9]+\\s*분?|[0-9]+\\s*분");
	}

	private String weekdayHeadway(List<String> lines) {
		String value = valueByPattern(lines, "평일\\s*([0-9]+\\s*(?:~|-)\\s*[0-9]+\\s*분?|[0-9]+\\s*분)");
		if (value != null && !value.isBlank()) {
			return value;
		}
		return headwayAfterLabel(lines);
	}

	private String headwayInSection(List<String> lines, String... labels) {
		for (String label : labels) {
			String value = valueByPattern(lines, label + "\\s*([0-9]+\\s*(?:~|-)\\s*[0-9]+\\s*분?|[0-9]+\\s*분)");
			if (value != null && !value.isBlank()) {
				return value;
			}
		}
		return null;
	}

	private String firstBusTimeInSection(List<String> lines, String... labels) {
		String value = timeInSection(lines, "첫차", labels);
		if (value != null && !value.isBlank()) {
			return value;
		}
		return timeRangeInSection(lines, true, labels);
	}

	private String lastBusTimeInSection(List<String> lines, String... labels) {
		String value = timeInSection(lines, "막차", labels);
		if (value != null && !value.isBlank()) {
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
						if (value != null && !value.isBlank()) {
							return value;
						}
					}
				}
			}
		}
		return "";
	}

	private String runsAfterLabel(List<String> lines) {
		String value = valueByPattern(lines, "현재\\s*([^\\s]+대\\s*운행중)");
		if (value != null && !value.isBlank()) {
			return value;
		}

		return valueNearLabel(lines, "현재", "[0-9]+\\s*대\\s*운행중");
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
		return null;
	}

	private String firstLineMatching(List<String> lines, Predicate<String> predicate) {
		return lines.stream()
			.filter(predicate)
			.findFirst()
			.orElse(null);
	}

	private String valueByPattern(List<String> lines, String regex) {
		Pattern pattern = Pattern.compile(regex);
		for (String line : lines) {
			Matcher matcher = pattern.matcher(line);
			if (matcher.find()) {
				return matcher.group(1).trim();
			}
		}
		return null;
	}

	private boolean looksLikeBusInfo(String line) {
		String normalized = line.toLowerCase(Locale.ROOT);
		return normalized.contains("버스")
			|| normalized.contains("정류장")
			|| normalized.contains("정류소")
			|| normalized.contains("도착")
			|| normalized.contains("출발")
			|| normalized.contains("간선")
			|| normalized.contains("지선")
			|| normalized.contains("광역")
			|| normalized.contains("마을")
			|| normalized.matches(".*\\b\\d{2,4}\\b.*");
	}

	private void validateHttpUrl(URI url) {
		String scheme = url.getScheme();
		if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
			throw new IllegalArgumentException("Only http and https URLs are supported.");
		}
	}
}
