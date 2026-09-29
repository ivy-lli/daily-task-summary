package com.axonivy.ivy;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;
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
        String rowTemplate = loadTemplate("DailyTaskRow.html");
        StringBuilder tasks = new StringBuilder();
        StringBuilder plainText = new StringBuilder("Your open tasks:\n\n");
        String logoUrl = null;
        int count = 0;
        for (ITask task : query.executor().resultsPaged()) {
          if (logoUrl == null) {
            String startUrl = task.getStartLink().getAbsolute();
            logoUrl = startUrl.substring(0, startUrl.indexOf("/pro/"))
                + "/faces/jakarta.faces.resource/logo_mail.png?ln=xpertivy-branding";
          }
          tasks.append(render(rowTemplate, Map.of(
              "NAME", escape(task.getName()),
              "ID", String.valueOf(task.getId()),
              "PRIORITY", escape(String.valueOf(task.getPriority())),
              "CREATED", formatDate(task.getStartTimestamp()),
              "EXPIRY", formatDate(task.getExpiryTimestamp()),
              "DESCRIPTION", escape(task.getDescription()),
              "START_URL", escape(task.getStartLink().getAbsolute()),
              "DETAIL_URL", escape(task.getDetailLink().getAbsolute()))));
          plainText.append(task.getName()).append(" (#").append(task.getId()).append(")\n")
              .append("Created: ").append(formatDate(task.getStartTimestamp()))
              .append(" | Expires: ").append(formatDate(task.getExpiryTimestamp())).append("\n")
              .append("Start Task: ").append(task.getStartLink().getAbsolute()).append("\n")
              .append("Task Details: ").append(task.getDetailLink().getAbsolute()).append("\n\n");
          count++;
        }
        if (count == 0) {
          continue;
        }

        String body = render(loadTemplate("DailyTaskSummary.html"), Map.of(
            "TASK_COUNT", String.valueOf(count),
            "TASK_NOUN", count == 1 ? "task" : "tasks",
            "TASK_ROWS", tasks.toString()));
        var message = MailMessage.create()
            .to(address)
            .subject("Daily task summary (" + count + ")")
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

  private static String render(String template, Map<String, String> values) {
    return PLACEHOLDER.matcher(template).replaceAll(match -> {
      String value = values.get(match.group(1));
      if (value == null) {
        throw new IllegalArgumentException("Unknown mail placeholder: " + match.group(1));
      }
      return Matcher.quoteReplacement(value);
    });
  }

  private static String formatDate(Date date) {
    return date == null ? "-" : DATE_FORMAT.format(date.toInstant().atZone(ZoneId.systemDefault()));
  }

  private static String escape(String value) {
    return value == null ? ""
        : value.replace("&", "&amp;").replace("<", "&lt;")
            .replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;");
  }
}