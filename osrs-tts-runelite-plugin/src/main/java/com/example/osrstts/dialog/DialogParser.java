package com.example.osrstts.dialog;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DialogParser {

    private static final Pattern DIALOG_PATTERN = Pattern.compile("(?<=\\[)(.*?)(?=\\])");

    public String parseDialog(String dialogText) {
        if (dialogText == null || dialogText.isEmpty()) {
            return "";
        }

        StringBuilder parsedDialog = new StringBuilder();
        Matcher matcher = DIALOG_PATTERN.matcher(dialogText);

        while (matcher.find()) {
            parsedDialog.append(matcher.group()).append(" ");
        }

        return parsedDialog.toString().trim();
    }
}