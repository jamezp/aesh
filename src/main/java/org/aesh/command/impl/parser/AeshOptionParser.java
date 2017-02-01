/*
 * JBoss, Home of Professional Open Source
 * Copyright 2014 Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the @authors tag. All rights reserved.
 * See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.aesh.command.impl.parser;

import org.aesh.command.impl.internal.OptionType;
import org.aesh.command.impl.internal.ProcessedOption;
import org.aesh.command.parser.OptionParser;
import org.aesh.parser.ParsedLineIterator;
import org.aesh.parser.ParsedWord;

/**
 * @author <a href="mailto:stale.pedersen@jboss.org">Ståle W. Pedersen</a>
 */
public class AeshOptionParser implements OptionParser {

    private static final String EQUALS = "=";
    private Status status;

    @Override
    public void parse(ParsedLineIterator parsedLineIterator, ProcessedOption option) {
        status = Status.NULL;
        doParse(parsedLineIterator.nextParsedWord(), option);
        if(option.hasMultipleValues())
            while(parsedLineIterator.hasNextWord()) {
                ParsedWord word = parsedLineIterator.nextParsedWord();
                ProcessedOption nextOption = option.parent().findOption(word.word());
                if(nextOption == null)
                    doParse(word, option);
                else
                    return;
            }
    }

    private void doParse(ParsedWord word, ProcessedOption option) {
            if(status == Status.ACTIVE)
                addValueToOption(option, word.word());
            else if(status == Status.NULL)
                preProcessOption(option, word.word());
    }

    private void preProcessOption(ProcessedOption option, String line) {
        if(option.isLongNameUsed()) {
            if(line.length()-2 != option.name().length())
                processOption(option, line.substring(2), option.name());
            else if(option.getOptionType() == OptionType.BOOLEAN) {
                option.addValue("true");
                //commandLine.addOption(option);
                status = Status.NULL;
            }
            else
                status = Status.OPTION_FOUND;

        }
        else {
            if(line.length() > 2)
                processOption(option, line.substring(1), option.shortName());
            else if(option.getOptionType() == OptionType.BOOLEAN) {
                option.addValue("true");
                //commandLine.addOption(option);
                status = Status.NULL;
            }
             else
                status = Status.OPTION_FOUND;
        }

        if(status == Status.OPTION_FOUND) {
            if(option.hasValue()) {
                //active = option;
                status = Status.ACTIVE;
            }
            else {
                //commandLine.addOption(option);
                status = Status.NULL;
            }
        }
    }
      private void processOption(ProcessedOption option, String line, String name) {
        if (option.isProperty()) {
            processProperty(option, line, name);
        }
        else {
            String rest = line.substring(name.length());
            if (option.getOptionType().equals(OptionType.LIST)) {
                processList(option, rest);
            }
            else if (!rest.contains(EQUALS)) {
                // we might have two or more options in a group
                // if so, we only allow options (boolean) without value
                if (rest.length() > 0 && !option.isLongNameUsed()) {
                    //first we add the first option
                    //commandLine.addOption(option);
                    for (char shortName : rest.toCharArray()) {
                        ProcessedOption currOption = option.parent().findOption(String.valueOf(shortName));
                        if (currOption != null) {
                            if (!currOption.hasValue()) {
                                currOption.setLongNameUsed(false);
                                currOption.addValue("true");
                                //commandLine.addOption(currOption);
                            }
                            else
                                option.parent().addParserException(new OptionParserException("Option: -" + shortName +
                                        " can not be grouped with other options since it need to be given a value"));
                        }
                        else
                            option.parent().addParserException(new OptionParserException("Option: -" + shortName + " was not found."));
                    }
                }
                else
                    option.parent().addParserException(new OptionParserException("Option: - must be followed by a valid operator"));
            }
            //line contain equals, we need to add a value(s) to the currentOption
            else {
                addValueToOption(option, line.substring(line.indexOf(EQUALS)+1));
            }
        }
    }

    private void addValueToOption(ProcessedOption currOption, String word) {
        if(currOption.hasMultipleValues()) {
            if(word.contains(String.valueOf(currOption.getValueSeparator()))) {
                for(String value : word.split(String.valueOf(currOption.getValueSeparator()))) {
                    currOption.addValue(value.trim());
                }
                if(word.endsWith(String.valueOf(currOption.getValueSeparator())))
                    currOption.setEndsWithSeparator(true);
                //commandLine.addOption(currOption);
                status = Status.NULL;
            }
            else {
                currOption.addValue(word);
                //active = currOption;
            }
        }
        else {
            currOption.addValue(word);
            //commandLine.addOption(currOption);
            status = Status.NULL;
        }
    }

    private void processList(ProcessedOption currOption, String rest) {
        if(rest.length() > 1 && rest.startsWith("=")) {
            if ( rest.indexOf(currOption.getValueSeparator()) > -1) {
                for (String value : rest.substring(1).split(String.valueOf(currOption.getValueSeparator()))) {
                    currOption.addValue(value.trim());
                }
                if (rest.endsWith(String.valueOf(currOption.getValueSeparator())))
                    currOption.setEndsWithSeparator(true);
            }
            else
                currOption.addValue(rest.substring(1));
            //commandLine.addOption(currOption);
            status = Status.NULL;
        }
    }

    private void processProperty(ProcessedOption currOption, String word, String name) {
        if (word.length() < (1 + name.length()) || !word.contains(EQUALS))
            currOption.parent().addParserException(new OptionParserException(
                    "Option " + currOption.getDisplayName() + ", must be part of a property"));
        else {
            String propertyName = word.substring(name.length(), word.indexOf(EQUALS));
            String value = word.substring(word.indexOf(EQUALS) + 1);
            if (value.length() < 1)
                currOption.parent().addParserException(new OptionParserException("Option " + currOption.getDisplayName() + ", must have a value"));
            else {
                currOption.addProperty(propertyName, value);
                //commandLine.addOption(currOption);
            }
        }
        status = Status.NULL;
    }

    private enum Status {
        NULL, OPTION_FOUND, ACTIVE;
    }
}
