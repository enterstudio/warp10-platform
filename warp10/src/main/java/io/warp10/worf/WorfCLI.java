//
//   Copyright 2016  Cityzen Data
//
//   Licensed under the Apache License, Version 2.0 (the "License");
//   you may not use this file except in compliance with the License.
//   You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
//   Unless required by applicable law or agreed to in writing, software
//   distributed under the License is distributed on an "AS IS" BASIS,
//   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//   See the License for the specific language governing permissions and
//   limitations under the License.
//
package io.warp10.worf;

import com.google.common.base.Strings;
import io.warp10.Revision;
import io.warp10.continuum.gts.GTSHelper;
import org.apache.commons.cli.*;
import org.codehaus.jettison.json.JSONObject;

import java.io.File;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

public class WorfCLI {
  public static boolean verbose = false;
  public static boolean quiet = false;


  private Options options = new Options();

  public static String UUIDGEN_PRODUCER = "puidg";
  public static String UUIDGEN_OWNER = "ouidg";

  public static String P_UUID = "puid";
  public static String O_UUID = "ouid";

  public static String APPNAME = "a";
  public static String LABELS = "l";

  private static String HELP = "h";
  private static String VERBOSE = "v";
  private static String VERSION = "version";
  private static String INTERACTIVE = "i";
  private static String OUTPUT = "o";
  private static String QUIET = "q";
  private static String TOKEN = "t";
  private static String TTL = "ttl";
  private static String KEYSTORE = "ks";

  public WorfCLI() {
    options.addOption(new Option(KEYSTORE, "keystore", true, "configuration file for generating tokens inside templates"));
    options.addOption(new Option(OUTPUT, "output", true, "output configuration destination file"));
    options.addOption(new Option(TOKEN, "tokens", false, "generate read/write tokens"));
    options.addOption(new Option("f", "format", false, "output tokens format. (default JSON)"));
    options.addOption(new Option(QUIET, "quiet", false, "Only tokens or error are written on the console"));

    OptionGroup groupProducerUID = new OptionGroup();
    groupProducerUID.addOption(new Option(UUIDGEN_PRODUCER, "producer-uuid-gen", false, "creates a new universally unique identifier for the producer"));
    groupProducerUID.addOption(new Option(P_UUID, "producer-uuid", true, "data producer uuid"));

    OptionGroup groupOwnerUID = new OptionGroup();
    groupOwnerUID.addOption(new Option(UUIDGEN_OWNER, "owner-uuid-gen", false, "creates a new universally unique identifier for the owner"));
    groupOwnerUID.addOption(new Option(O_UUID, "owner-uuid", true, "data owner uuid (producer uuid by default)"));

    options.addOptionGroup(groupProducerUID);
    options.addOptionGroup(groupOwnerUID);

    options.addOption(new Option(APPNAME, "app-name", true, "token application name. Used by token option or @warp:writeToken@ template"));
    options.addOption(new Option(LABELS, "labels", true, "enclosed label list for write token (following ingress input format : xbeeId=XBee_40670F0D,moteId=53)"));
    options.addOption(new Option(TTL, "ttl", true, "token time to live (ms). Used by token option or @warp:writeToken@ template"));


    options.addOption(HELP, "help", false, "show help.");
    options.addOption(VERBOSE, "verbose", false, "Verbose mode");
    options.addOption(VERSION, "version", false, "Print version number and return");
    options.addOption(INTERACTIVE, "interactive", false, "Interactive mode, all other options are ignored");
  }

  public static void consolePrintln(String message, PrintWriter out) {
    if (!quiet) {
      out.println(message);
      out.flush();
    }
  }

  public int execute(String[] args) {
    try {
      CommandLineParser parser = new BasicParser();
      CommandLine cmd = parser.parse(options, args);

      String inputFile = null;
      boolean interactive = false;
      boolean token = false;
      String outputFile = null;
      String keyStoreFile = null;

      String producerUID = null;
      String ownerUID = null;
      String appName = null;
      String lbs = null;
      Map<String, String> labels = null;
      boolean labelMap = false;
      long ttl = 0L;

      PrintWriter out = new PrintWriter(System.out);

      if (cmd.hasOption(HELP)) {
        help();
        return 0;
      }

      if (cmd.hasOption(VERSION)) {
        version(out);
        return 0;
      }

      if (cmd.hasOption(VERBOSE)) {
        verbose = true;
      }

      if (cmd.hasOption(INTERACTIVE)) {
        interactive = true;
      }

      if (cmd.hasOption(QUIET)) {
        quiet = true;
      }

      if (cmd.hasOption(OUTPUT)) {
        outputFile = cmd.getOptionValue(OUTPUT);
      }

      if (cmd.hasOption(KEYSTORE)) {
        keyStoreFile = cmd.getOptionValue(KEYSTORE);
      }

      if (cmd.hasOption(TOKEN)) {
        token = true;
        // PRODUCER UUID OPTION (mandatory)
        // --------------------------------------------------------------------
        if (cmd.hasOption(UUIDGEN_PRODUCER)) {
          producerUID = UUID.randomUUID().toString();
        } else if (cmd.hasOption(P_UUID)) {
          producerUID = cmd.getOptionValue(P_UUID);
          UUID.fromString(producerUID);
        }

        // OWNER UUID OPTION (mandatory)
        // --------------------------------------------------------------------
        if (cmd.hasOption(UUIDGEN_OWNER)) {
          ownerUID = UUID.randomUUID().toString();
        } else if (cmd.hasOption(O_UUID)) {
          ownerUID = cmd.getOptionValue(O_UUID);
          UUID.fromString(ownerUID);
        } else {
          ownerUID = producerUID;
        }

        if (cmd.hasOption(APPNAME)) {
          appName = cmd.getOptionValue(APPNAME);
        }

        if (cmd.hasOption(LABELS)) {
          lbs = cmd.getOptionValue(LABELS);
          try {
            labels = GTSHelper.parseLabels(lbs);
            labelMap = true;
          }
          catch ( Exception e) {
            throw new WorfException("Not a valid label selector : " + e.getMessage());
          }
        }


        if (cmd.hasOption(TTL)) {
          ttl = Long.parseLong(cmd.getOptionValue(TTL));
          long ttlMax = Long.MAX_VALUE - System.currentTimeMillis();

          if (ttl >= ttlMax) {
            throw new WorfException("TTL can not be upper than " + ttlMax + " ms") ;
          }

        } else {
          throw new WorfException("The option 'ttl' is missing ");
        }
      }

      // get the input file name
      switch (cmd.getArgs().length) {
        case 0:
          throw new WorfException("Config or template file missing.");

        case 1:
          inputFile = cmd.getArgs()[0];
          break;

        default:
          throw new WorfException("Too many arguments, only one config or template file expected.");
      }

      // load the interactive mode
      if (interactive) {
        return runInteractive(inputFile);
      }

      Properties config = Worf.readConfig(inputFile, out);

      //
      // TEMPLATE CONFIGURATION
      //
      if (WorfTemplate.isTemplate(config)) {

        // load keystore if needed
        WorfKeyMaster templateKeyMaster = null;
        if (!Strings.isNullOrEmpty(keyStoreFile)) {
          // read config
          Properties keyStoreConfig = Worf.readConfig(keyStoreFile, out);
          // load key master
          templateKeyMaster = new WorfKeyMaster(keyStoreConfig);

          if (!templateKeyMaster.loadKeyStore()) {
            throw new WorfException("Template Keystore not loaded");
          }
        }

        WorfTemplate tpl = new WorfTemplate(config, inputFile);

        // GENERATE CRYPTO KEYS
        for (String cryptoKey : tpl.getCryptoKeys()) {
          String keySize = tpl.generateCryptoKey(cryptoKey);
          if (keySize != null) {
            consolePrintln(keySize + " bits secured key for " + cryptoKey + "  generated", out);
          } else {
            throw new WorfException("Unable to generate " + cryptoKey + ", template error");
          }
        }

        // read defaults
        if (token) {
          Properties defaultProperties = Worf.readDefault(inputFile, out);
          appName = Worf.getDefault(defaultProperties, out, appName, APPNAME);
          producerUID = Worf.getDefault(defaultProperties, out, producerUID, P_UUID);
          ownerUID = Worf.getDefault(defaultProperties, out, ownerUID, O_UUID);
        }
        // GENERATE TOKENS
        for(String tokenKey: tpl.getTokenKeys()) {
          if (!token) {
            throw new WorfException("Unable to generate template tokens missing -t option");
          }
          if (templateKeyMaster == null) {
            throw new WorfException("Unable to generate template tokens missing -ks option");
          }

          String tokenIdent = tpl.generateTokenKey(tokenKey, appName, ownerUID, producerUID, ttl, templateKeyMaster);
          consolePrintln("Token generated key=" + tokenKey + "  ident=" + tokenIdent, out);
        }

        // GET INTERACTIVE CONFIGURATION
        if (tpl.getFieldsStack().size() > 0) {
          throw new WorfException("Unable the update template, you are not in interactive mode");
        }

        // save the template
        if (Strings.isNullOrEmpty(outputFile)) {
          Path inputConfigurationPath = Paths.get(inputFile);
          String outputFileName = inputConfigurationPath.getFileName().toString();
          outputFileName = outputFileName.replace("template", "conf");

          StringBuilder sb = new StringBuilder();
          sb.append(inputConfigurationPath.getParent().toString());
          if (!inputConfigurationPath.endsWith(File.separator)) {
            sb.append(File.separator);
          }
          sb.append(outputFileName);

          outputFile = sb.toString();
        }
        tpl.saveConfig(outputFile);
        consolePrintln("Warp configuration saved (" + outputFile + ")", out);
        inputFile = outputFile;

        // Keystore is given as input
        //end of the job
        if (!Strings.isNullOrEmpty(keyStoreFile)) {
          consolePrintln("Warp configuration file used for tokens generation in templates", out);
          consolePrintln("For generate tokens, reload Worf without 'ks' option", out);
          System.exit(0);
        }
      }

      //
      // GENERATE TOKEN
      //
      if (token) {
        // read config
        config = Worf.readConfig(inputFile, out);
        // load key master
        WorfKeyMaster keyMaster = new WorfKeyMaster(config);

        if (!keyMaster.loadKeyStore()) {
          throw new WorfException("Keystore not loaded");
        }

        Properties defaultProperties = Worf.readDefault(inputFile, out);
        appName = Worf.getDefault(defaultProperties, out, appName, APPNAME);
        producerUID = Worf.getDefault(defaultProperties, out, producerUID, P_UUID);
        ownerUID = Worf.getDefault(defaultProperties, out, ownerUID, O_UUID);
        String writeToken = null;

        // save default
        if (defaultProperties == null) {
          Worf.saveDefault(inputFile, appName, producerUID, ownerUID);
        }

        // deliver token
        String readToken = keyMaster.deliverReadToken(appName, producerUID, ownerUID, ttl);
        if (labelMap) {
           writeToken = keyMaster.deliverWriteToken(appName, producerUID, ownerUID, labels, ttl);
        } else {
           writeToken = keyMaster.deliverWriteToken(appName, producerUID, ownerUID,  ttl);
        }

        // write outputformat
        JSONObject jsonOutput = new JSONObject();
        JSONObject jsonToken = new JSONObject();
        jsonToken.put("token", readToken);
        jsonToken.put("tokenIdent", keyMaster.getTokenIdent(readToken));
        jsonToken.put("ttl", ttl);
        jsonToken.put("application", appName);
        jsonToken.put("owner", ownerUID);
        jsonToken.put("producer", producerUID);

        jsonOutput.put("read", jsonToken);

        jsonToken = new JSONObject();
        jsonToken.put("token", writeToken);
        jsonToken.put("tokenIdent", keyMaster.getTokenIdent(writeToken));
        jsonToken.put("ttl", ttl);
        jsonToken.put("application", appName);
        jsonToken.put("owner", ownerUID);
        jsonToken.put("producer", producerUID);
        if (labelMap) {
          jsonToken.put("labels", labels.toString());
        }
        jsonOutput.put("write", jsonToken);

        System.out.println(jsonOutput.toString());
      }

    } catch (Exception we) {
      if (verbose) {
        we.printStackTrace();
      } else {
        System.out.println(we.getMessage() + "\n");
      }
      help();
      return -1;
    }
    return 0;
  }

  private static int runInteractive(String warp10Configuration) throws WorfException {
    WorfInteractive worfInteractive = new WorfInteractive();
    PrintWriter out = worfInteractive.getPrintWriter();

    // read warp10 configuration
    consolePrintln("Reading warp10 configuration " + warp10Configuration, out);
    Properties config = Worf.readConfig(warp10Configuration, out);

    if (config == null) {
      consolePrintln("Unable to read warp10 configuration.", out);
      return -1;
    }

    if (WorfTemplate.isTemplate(config)) {
      warp10Configuration = worfInteractive.runTemplate(config, warp10Configuration);
      // reload config
      config = Worf.readConfig(warp10Configuration, out);
    }
    // get Default values
    Properties worfConfig = Worf.readDefault(warp10Configuration, out);
    worfInteractive.run(config, worfConfig);

    return 0;
  }


  private void help() {
    // This prints out some help
    HelpFormatter formatter = new HelpFormatter();

    String header = "DESCRIPTION";
    String footer = " \n \nCOPYRIGHT\nCopyright © 2016 Cityzen Data.\n Licensed under the Apache License, Version 2.0 (the \"License\")\n" +
        "you may not use this file except in compliance with the License.\n" +
        "You may obtain a copy of the License at\n" +
        "http://www.apache.org/licenses/LICENSE-2.0";

    formatter.printHelp("worf [OPTION] SOURCE_CONFIG [-o OUTPUT_CONFIG]", header,  options, footer, false);
    System.exit(0);
  }

  private void version(PrintWriter out) {
    out.println("Warp 10 revision " + Revision.REVISION);
    System.exit(0);
  }
}

