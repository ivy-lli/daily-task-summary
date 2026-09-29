package com.axonivy.ivy;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import ch.ivyteam.ivy.environment.Ivy;
import ch.ivyteam.ivy.mail.MailAttachment;
import ch.ivyteam.ivy.mail.MailClient;
import ch.ivyteam.ivy.mail.MailMessage;
import ch.ivyteam.ivy.security.IUser;
import ch.ivyteam.ivy.workflow.ITask;
import ch.ivyteam.ivy.workflow.query.TaskQuery;
import ch.ivyteam.ivy.workflow.task.TaskBusinessState;

public class DailyTaskSummary {

  private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
  private static final DateTimeFormatter GERMAN_DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");
  private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{([A-Z_]+)\\}\\}");

  public static void send() {
    try (MailClient mail = MailClient.create()) {
      for (IUser user : Ivy.security().users().paged()) {
        String address = user.getEMailAddress();
        if (address == null || address.isBlank()) {
          continue;
        }

        TaskQuery query = TaskQuery.create()
            .where().businessState().isIn(TaskBusinessState.OPEN, TaskBusinessState.IN_PROGRESS)
            .and().canWorkOn(user);
        Locale userLanguage = user.getLanguage();
        Locale language = userLanguage != null && "de".equals(userLanguage.getLanguage())
            ? Locale.GERMAN
            : Locale.ENGLISH;
        String rowTemplate = loadTemplate("DailyTaskRow.html");
        StringBuilder tasks = new StringBuilder();
        StringBuilder plainText = new StringBuilder(cms(language, "plainHeading")).append("\n\n");
        String logoUrl = null;
        int count = 0;
        for (ITask task : query.executor().resultsPaged()) {
          if (logoUrl == null) {
            String startUrl = task.getStartLink().getAbsolute();
            logoUrl = startUrl.substring(0, startUrl.indexOf("/pro/"))
                + "/faces/jakarta.faces.resource/logo_mail.png?ln=xpertivy-branding";
          }
          tasks.append(render(rowTemplate, Map.ofEntries(
              Map.entry("NAME", escape(task.getName())),
              Map.entry("ID", String.valueOf(task.getId())),
              Map.entry("PRIORITY", escape(cms(language, "priority" + task.getPriority().name()))),
              Map.entry("CREATED", formatDate(task.getStartTimestamp(), language)),
              Map.entry("EXPIRY", formatDate(task.getExpiryTimestamp(), language)),
              Map.entry("DESCRIPTION", escape(task.getDescription())),
              Map.entry("START_URL", escape(task.getStartLinkEmbedded().getAbsolute())),
              Map.entry("DETAIL_URL", escape(task.getDetailLink().getAbsolute())),
              Map.entry("TASK_LABEL", escape(cms(language, "task"))),
              Map.entry("PRIORITY_LABEL", escape(cms(language, "priority"))),
              Map.entry("CREATED_LABEL", escape(cms(language, "created"))),
              Map.entry("EXPIRES_LABEL", escape(cms(language, "expires"))),
              Map.entry("START_LABEL", escape(cms(language, "start"))),
              Map.entry("DETAILS_LABEL", escape(cms(language, "details"))))));
          plainText.append(task.getName()).append(" (#").append(task.getId()).append(")\n")
              .append(cms(language, "plainCreated")).append(' ').append(formatDate(task.getStartTimestamp(), language))
              .append(" | ").append(cms(language, "plainExpires")).append(' ')
              .append(formatDate(task.getExpiryTimestamp(), language)).append("\n")
              .append(cms(language, "start")).append(": ").append(task.getStartLinkEmbedded().getAbsolute())
              .append("\n")
              .append(cms(language, "details")).append(": ").append(task.getDetailLink().getAbsolute()).append("\n\n");
          count++;
        }
        if (count == 0) {
          continue;
        }

        String body = render(loadTemplate("DailyTaskSummary.html"), Map.of(
            "TASK_COUNT", String.valueOf(count),
            "LANGUAGE", escape(language.getLanguage()),
            "HEADING", escape(cms(language, "heading")),
            "INTRO", escape(render(cms(language, count == 1 ? "introOne" : "introMany"),
                Map.of("TASK_COUNT", String.valueOf(count)))),
            "TASK_ROWS", tasks.toString()));
        var message = MailMessage.create()
            .to(address)
            .subject(render(cms(language, "subject"),
                Map.of("TASK_COUNT", String.valueOf(count))))
            .textContent(plainText.toString())
            .htmlContent(body);
        String brandingUrl = logoUrl;
        message.attachments(MailAttachment.create()
            .inputStream(() -> {
              try {
                return URI.create(brandingUrl).toURL().openStream();
              } catch (IOException exception) {
                throw new UncheckedIOException(exception);
              }
            }, "image/png")
            .fileName("logo_mail.png")
            .inline("daily-summary-logo")
            .toMailAttachment());
        mail.send(message.toMailMessage());
      }
    } catch (Exception exception) {
      throw new IllegalStateException("Could not send daily task summaries", exception);
    }
  }

  private static String loadTemplate(String name) {
    try (InputStream stream = DailyTaskSummary.class.getResourceAsStream(name)) {
      if (stream == null) {
        throw new IllegalStateException("Missing mail template: " + name);
      }
      return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException exception) {
      throw new UncheckedIOException(exception);
    }
  }

  private static String cms(Locale language, String name) {
    return Ivy.cms().coLocale("/DailyTaskSummary/" + name, language);
  }

  private static String render(String template, Map<String, String> values) {
    return PLACEHOLDER.matcher(template).replaceAll(match -> {
      String value = values.get(match.group(1));
      if (value == null) {
        throw new IllegalArgumentException("Unknown mail placeholder: " + match.group(1));
      }
      return Matcher.quoteReplacement(value);
    });
  }

  private static String formatDate(Date date, Locale language) {
    DateTimeFormatter format = "de".equals(language.getLanguage()) ? GERMAN_DATE_FORMAT : DATE_FORMAT;
    return date == null ? "-" : format.format(date.toInstant().atZone(ZoneId.systemDefault()));
  }

  private static String escape(String value) {
    return value == null ? ""
        : value.replace("&", "&amp;").replace("<", "&lt;")
            .replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;");
  }
}