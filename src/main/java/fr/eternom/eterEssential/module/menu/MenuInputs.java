package fr.eternom.eterEssential.module.menu;

import fr.eternom.eterLib.helper.gui.Dialogs;
import fr.eternom.eterLib.helper.message.Messages;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Les commandes du menu qui attendent une valeur : {player} (choix dans la liste des joueurs connectés), {amount}
 * (un montant) et {text} (un message), demandés l'un après l'autre avant de lancer la commande. Annuler, ou une
 * valeur invalide, ramène à la page du menu (back) au lieu de tout fermer.
 */
final class MenuInputs {

    private static final String PLAYER = "{player}";
    private static final String AMOUNT = "{amount}";
    private static final String TEXT = "{text}";
    private static final String FIELD = "value";
    private static final Pattern NUMBER = Pattern.compile("\\d{1,12}(\\.\\d{1,2})?");

    private MenuInputs() {
    }

    /** La commande demande quelque chose avant d'être lancée. */
    static boolean asks(String command) {
        return command.contains(PLAYER) || command.contains(AMOUNT) || command.contains(TEXT);
    }

    /** Demande les valeurs manquantes une à une, puis lance la commande. */
    static void run(MenuCommand menus, Player player, String command, Runnable back) {
        if (command.contains(PLAYER)) {
            player.openInventory(new PlayerPicker(menus, player, command, back, 0).getInventory());
        } else if (command.contains(AMOUNT)) {
            ask(menus, player, command, AMOUNT, "amount", back);
        } else if (command.contains(TEXT)) {
            ask(menus, player, command, TEXT, "text", back);
        } else {
            player.closeInventory();
            player.performCommand(command);
        }
    }

    /** Remplace le premier {player} : la suite (montant, message) est demandée ensuite. */
    static void withPlayer(MenuCommand menus, Player player, String command, String name, Runnable back) {
        run(menus, player, command.replaceFirst(Pattern.quote(PLAYER), name), back);
    }

    private static void ask(MenuCommand menus, Player player, String command, String placeholder, String kind, Runnable back) {
        Messages messages = menus.messages();
        // La commande telle qu'elle sera lancée, la valeur demandée en évidence
        String shown = "/" + command.replace(placeholder, "?").replace(AMOUNT, "?").replace(TEXT, "?");
        DialogBase base = DialogBase.builder(messages.get(player, "menu.input." + kind + ".title"))
                .body(List.of(DialogBody.plainMessage(messages.get(player, "menu.input.body", "command", shown))))
                .inputs(List.of(DialogInput.text(FIELD, messages.get(player, "menu.input." + kind + ".label"))
                        .maxLength(kind.equals("amount") ? 15 : 200)
                        .build()))
                .build();
        player.closeInventory();
        Dialogs.show(menus.plugin(), player, base, messages.get(player, "menu.input.confirm"), messages.get(player, "menu.input.cancel"),
                response -> {
                    String value = clean(kind, response.getText(FIELD));
                    if (value == null) {
                        messages.send(player, "menu.input." + kind + ".invalid");
                        back.run();
                        return;
                    }
                    run(menus, player, command.replace(placeholder, value), back);
                }, back);
    }

    /** null si la saisie ne convient pas. Montant : nombre positif, la virgule acceptée. Texte : une ligne. */
    private static String clean(String kind, String input) {
        if (input == null) {
            return null;
        }
        String value = input.strip();
        if (kind.equals("amount")) {
            value = value.replace(',', '.');
            return NUMBER.matcher(value).matches() && Double.parseDouble(value) > 0 ? value : null;
        }
        value = value.replaceAll("\\s+", " ");
        return value.isEmpty() ? null : value;
    }
}
