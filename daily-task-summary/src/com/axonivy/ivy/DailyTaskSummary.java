package com.axonivy.ivy;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;

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
          tasks.append("<tr><td style='padding:16px 0;border-top:1px solid #e2e5e9'>")
              .append("<strong style='font-size:16px;color:#27313b'>").append(escape(task.getName())).append("</strong>")
              .append("<div style='margin-top:8px;color:#59636e'>Task #").append(task.getId())
              .append(" &nbsp;|&nbsp; Priority: ").append(escape(String.valueOf(task.getPriority())))
              .append(" &nbsp;|&nbsp; Date created: ").append(formatDate(task.getStartTimestamp()))
              .append(" &nbsp;|&nbsp; Expiry date: ").append(formatDate(task.getExpiryTimestamp()))
              .append("</div>");
          if (task.getDescription() != null && !task.getDescription().isBlank()) {
            tasks.append("<div style='margin-top:8px;color:#59636e'>")
                .append(escape(task.getDescription())).append("</div>");
          }
          tasks.append("<div style='margin-top:12px'><a style='color:#087d8c;font-weight:bold' href='")
              .append(escape(task.getStartLink().getAbsolute()))
              .append("'>Start Task</a> &nbsp; <a style='color:#087d8c' href='")
              .append(escape(task.getDetailLink().getAbsolute()))
              .append("'>Task Details</a></div></td></tr>");
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

        String logoHtml = "<img src='cid:daily-summary-logo' alt='Logo' style='max-width:160px;height:auto'>";
        String body = "<html><body style='margin:0;background:#f4f6f7;font-family:Arial,sans-serif;color:#27313b'>"
          + "<table role='presentation' style='width:100%;border-collapse:collapse'><tr><td align='center' style='padding:24px 12px'>"
          + "<table role='presentation' style='width:100%;max-width:640px;border-collapse:collapse;background:#fff'>"
          + "<tr><td style='padding:28px 32px;border-bottom:1px solid #e2e5e9'>" + logoHtml + "</td></tr>"
          + "<tr><td style='padding:24px 32px'><h2 style='margin:0 0 16px;font-size:22px'>Your open tasks</h2>"
          + "<p style='margin:0 0 20px;color:#59636e'>You have " + count + " open task" + (count == 1 ? "" : "s") + ".</p>"
          + "<table role='presentation' style='width:100%;border-collapse:collapse'>" + tasks + "</table>"
          + "</td></tr></table></td></tr></table></body></html>";
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

  private static String formatDate(Date date) {
    return date == null ? "-" : DATE_FORMAT.format(date.toInstant().atZone(ZoneId.systemDefault()));
  }

  private static String escape(String value) {
    return value == null ? "" : value.replace("&", "&amp;").replace("<", "&lt;")
        .replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;");
  }
}