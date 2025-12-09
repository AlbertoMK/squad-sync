package com.squadsync.backend.service;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;

@Service
public class DiscordBotService {

    @Value("${discord.bot.token}")
    private String botToken;

    @Value("${discord.bot.channel-id}")
    private String defaultChannelId;

    private JDA jda;

    @PostConstruct
    public void init() {
        if ("DUMMY_TOKEN".equals(botToken)) {
            System.out.println("Discord Bot Token is DUMMY_TOKEN. Bot will not start.");
            return;
        }
        try {
            jda = JDABuilder.createDefault(botToken).build();
            jda.awaitReady();
            System.out.println("Discord Bot started successfully!");
        } catch (Exception e) {
            System.err.println("Failed to start Discord Bot: " + e.getMessage());
        }
    }

    public void sendMatchmakingUpdates(java.util.List<com.squadsync.backend.model.GameSession> sessions) {
        if (jda == null) {
            System.out.println("JDA is not initialized. Cannot send updates.");
            return;
        }
        if (sessions == null || sessions.isEmpty()) {
            System.out.println("No sessions to report.");
            return;
        }

        TextChannel channel = jda.getTextChannelById(defaultChannelId); // Use the configured channel
        if (channel == null) {
            System.err.println("Channel not found: " + defaultChannelId); // Retry lookup if null? mostly likely config
                                                                          // error
            return;
        }

        for (com.squadsync.backend.model.GameSession session : sessions) {
            net.dv8tion.jda.api.EmbedBuilder embed = new net.dv8tion.jda.api.EmbedBuilder();

            // Calculate status for display
            // We replicate basic logic here or usage helper.
            // Ideally should be passed in, but signature change is larger.
            // Let's rely on the fact that this method is called for Confirmed sessions
            // usually,
            // OR checks the status.

            // Re-implementing basic status check for display purposes:
            long acceptedPlayers = session.getPlayers().stream()
                    .filter(p -> p
                            .getStatus() == com.squadsync.backend.model.GameSessionPlayer.SessionPlayerStatus.ACCEPTED)
                    .count();
            int minPlayers = Math.max(2, session.getGame().getMinPlayers());
            boolean enoughPlayers = acceptedPlayers >= minPlayers;
            boolean startsSoon = session.getStartTime().isBefore(java.time.LocalDateTime.now().plusHours(1));

            boolean isConfirmed = enoughPlayers && startsSoon;

            // Color based on status
            if (isConfirmed) {
                embed.setColor(java.awt.Color.GREEN);
                embed.setTitle("✅ Sesión Confirmada: " + session.getGame().getTitle());
            } else {
                embed.setColor(java.awt.Color.YELLOW);
                embed.setTitle("⚠️ Sesión Preliminar: " + session.getGame().getTitle());
            }

            // Description / Fields
            java.time.format.DateTimeFormatter formatter = java.time.format.DateTimeFormatter.ofPattern("dd/MM HH:mm");
            embed.addField("Fecha de Inicio", session.getStartTime().format(formatter), true);

            long durationMinutes = java.time.Duration.between(session.getStartTime(), session.getEndTime()).toMinutes();
            embed.addField("Duración", durationMinutes + " minutos", true);

            int playerCount = session.getPlayers().size();
            embed.addField("Jugadores", String.valueOf(playerCount), true);

            embed.setFooter("ID: " + session.getId());
            embed.setTimestamp(java.time.Instant.now());

            String messageContent = "";
            if (isConfirmed) {
                messageContent = "@here";
            }
            channel.sendMessage(messageContent).setEmbeds(embed.build()).queue();
            System.out.println("Sent update for session: " + session.getId());
        }
    }

    public void sendPreliminarySessionNotifications(java.util.List<com.squadsync.backend.model.GameSession> sessions) {
        if (jda == null)
            return;
        if (sessions == null || sessions.isEmpty())
            return;

        TextChannel channel = jda.getTextChannelById(defaultChannelId);
        if (channel == null) {
            System.err.println("Channel not found: " + defaultChannelId);
            return;
        }

        for (com.squadsync.backend.model.GameSession session : sessions) {
            net.dv8tion.jda.api.EmbedBuilder embed = new net.dv8tion.jda.api.EmbedBuilder();

            // Orange for Preliminary
            embed.setColor(java.awt.Color.ORANGE);
            embed.setTitle("⚠️ Sesión Preliminar (Comienza pronto): " + session.getGame().getTitle());

            // Description / Fields
            java.time.format.DateTimeFormatter formatter = java.time.format.DateTimeFormatter.ofPattern("dd/MM HH:mm");
            embed.addField("Fecha de Inicio", session.getStartTime().format(formatter), true);

            long durationMinutes = java.time.Duration.between(session.getStartTime(), session.getEndTime()).toMinutes();
            embed.addField("Duración", durationMinutes + " minutos", true);

            int playerCount = session.getPlayers().size();
            embed.addField("Jugadores actuales", String.valueOf(playerCount), true);

            embed.setFooter("ID: " + session.getId());
            embed.setTimestamp(java.time.Instant.now());

            // Build mentions string
            StringBuilder mentions = new StringBuilder();
            for (com.squadsync.backend.model.GameSessionPlayer player : session.getPlayers()) {
                String discordId = player.getUser().getDiscordId();

                if (discordId != null && !discordId.isBlank()) {
                    mentions.append("<@").append(discordId).append(">").append(" ");
                }
            }

            String messageContent = mentions.toString().trim();
            if (messageContent.isEmpty()) {
                channel.sendMessageEmbeds(embed.build()).queue();
            } else {
                channel.sendMessage(messageContent).setEmbeds(embed.build()).queue();
            }
            System.out.println("Sent preliminary notification for session: " + session.getId());
        }
    }
}
