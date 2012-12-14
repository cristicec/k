package org.kframework.main;

import com.thoughtworks.xstream.XStream;
import org.apache.commons.cli.CommandLine;
import org.kframework.backend.Backend;
import org.kframework.backend.html.HtmlBackend;
import org.kframework.backend.latex.LatexBackend;
import org.kframework.backend.latex.PdfBackend;
import org.kframework.backend.maude.MaudeBackend;
import org.kframework.backend.unparser.UnparserBackend;
import org.kframework.backend.xml.XmlBackend;
import org.kframework.compile.AddEval;
import org.kframework.compile.FlattenModules;
import org.kframework.compile.ResolveConfigurationAbstraction;
import org.kframework.compile.checks.CheckRewrite;
import org.kframework.compile.checks.CheckVariables;
import org.kframework.compile.sharing.AutomaticModuleImportsTransformer;
import org.kframework.compile.sharing.DittoFilter;
import org.kframework.compile.tags.AddDefaultComputational;
import org.kframework.compile.tags.AddOptionalTags;
import org.kframework.compile.tags.AddStrictStar;
import org.kframework.compile.transformers.*;
import org.kframework.compile.utils.*;
import org.kframework.kil.Definition;
import org.kframework.kil.loader.DefinitionHelper;
import org.kframework.kompile.lint.InfiniteRewrite;
import org.kframework.kompile.lint.KlintRule;
import org.kframework.kompile.lint.UnusedName;
import org.kframework.kompile.lint.UnusedSyntax;
import org.kframework.utils.Stopwatch;
import org.kframework.utils.errorsystem.KException;
import org.kframework.utils.errorsystem.KException.ExceptionType;
import org.kframework.utils.errorsystem.KException.KExceptionGroup;
import org.kframework.utils.file.FileUtil;
import org.kframework.utils.file.KPaths;
import org.kframework.utils.general.GlobalSettings;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

public class KompileFrontEnd {
	private static List<String> metadataParse(String tags) {
		String[] alltags = tags.split("\\s+");
		List<String> result = new ArrayList<String>();
		for (int i = 0; i < alltags.length; i++)
			result.add(alltags[i]);
		return result;
	}

	public static void kompile(String[] args) {
		Stopwatch sw = new Stopwatch();
		KompileOptionsParser op = new KompileOptionsParser();

		CommandLine cmd = op.parse(args);

		// options: help
		if (cmd.hasOption("help"))
			org.kframework.utils.Error.helpExit(op.getHelp(), op.getOptions());

		if (cmd.hasOption("version")) {
			String msg = FileUtil.getFileContent(KPaths.getKBase(false) + "/bin/version.txt");
			System.out.println(msg);
			System.exit(0);
		}

		// set verbose
		if (cmd.hasOption("verbose"))
			GlobalSettings.verbose = true;

		if (cmd.hasOption("nofilename"))
			GlobalSettings.noFilename = true;

		if (cmd.hasOption("warnings"))
			GlobalSettings.hiddenWarnings = true;

		if (cmd.hasOption("transition"))
			GlobalSettings.transition = metadataParse(cmd.getOptionValue("transition"));
		if (cmd.hasOption("supercool"))
			GlobalSettings.supercool = metadataParse(cmd.getOptionValue("supercool"));
		if (cmd.hasOption("superheat"))
			GlobalSettings.superheat = metadataParse(cmd.getOptionValue("superheat"));

		if (cmd.hasOption("style")) {
			String style = cmd.getOptionValue("style");
			if (style.startsWith("+")) {
				GlobalSettings.style += style.replace("+", ",");
			} else {
				GlobalSettings.style = style;
			}
		}

		if (cmd.hasOption("addTopCell"))
			GlobalSettings.addTopCell = true;

		// set lib if any
		if (cmd.hasOption("lib")) {
			GlobalSettings.lib = cmd.getOptionValue("lib");
		}
		if (cmd.hasOption("syntax-module"))
			GlobalSettings.synModule = cmd.getOptionValue("syntax-module");

		String step = null;
		if (cmd.hasOption("step")) {
			step = cmd.getOptionValue("step");
		}

		if (cmd.hasOption("fromxml")) {
//			File xmlFile = new File(cmd.getOptionValue("fromxml"));
//			if (cmd.hasOption("lang"))
//				fromxml(xmlFile, cmd.getOptionValue("lang"), step);
//			else
//				fromxml(xmlFile, FileUtil.getMainModule(xmlFile.getName()), step);
			System.err.println("fromxml option not supported anymore");
			System.exit(0);
		}

		String def = null;
		if (cmd.hasOption("def"))
			def = cmd.getOptionValue("def");
		else {
			String[] restArgs = cmd.getArgs();
			if (restArgs.length < 1)
				GlobalSettings.kem.register(new KException(ExceptionType.ERROR, KExceptionGroup.CRITICAL, "You have to provide a file in order to compile!.", "command line", "System file."));
			else
				def = restArgs[0];
		}

		File mainFile = new File(def);
		GlobalSettings.mainFile = mainFile;
		GlobalSettings.mainFileWithNoExtension = mainFile.getAbsolutePath().replaceFirst("\\.k$", "").replaceFirst("\\.xml$", "");
		if (!mainFile.exists()) {
			File errorFile = mainFile;
			mainFile = new File(def + ".k");
			if (!mainFile.exists())
				GlobalSettings.kem.register(new KException(ExceptionType.ERROR, KExceptionGroup.CRITICAL, "File: " + errorFile.getName() + "(.k) not found.", errorFile.getAbsolutePath(),
						"File system."));
		}

		// DefinitionHelper.dotk = new File(mainFile.getCanonicalFile().getParent() + File.separator + FileUtil.stripExtension(mainFile.getName()) + "-compiled");
		if (DefinitionHelper.dotk == null) {
			try {
				DefinitionHelper.dotk = new File(mainFile.getCanonicalFile().getParent() + File.separator + ".k");
			} catch (IOException e) {
				GlobalSettings.kem.register(new KException(ExceptionType.ERROR, KExceptionGroup.CRITICAL, "Output dir cannot be used.", mainFile.getAbsolutePath(), "File system."));
			}
		}
		DefinitionHelper.dotk.mkdirs();

		String lang = null;
		if (cmd.hasOption("lang"))
			lang = cmd.getOptionValue("lang");
		else
			lang = FileUtil.getMainModule(mainFile.getName());

		if (cmd.hasOption("lint")) {
			lint(mainFile, lang);
		}

		Backend backend = null;
		if (cmd.hasOption("maudify")) {
			backend = new MaudeBackend();
		} else if (cmd.hasOption("latex")) {
			backend = new LatexBackend();
		} else if (cmd.hasOption("pdf")) {
			backend = new PdfBackend();
		} else if (cmd.hasOption("xml")) {
			backend = new XmlBackend();
		} else if (cmd.hasOption("html")) {
			backend = new HtmlBackend();
		} else if (cmd.hasOption("unparse")) {
			backend = new UnparserBackend();
		} else {
			backend = new KompileBackend();
		}
		if (backend != null) {
			genericCompile(mainFile, lang, backend, step);	
		}
		if (GlobalSettings.verbose)
			sw.printTotal("Total");
		GlobalSettings.kem.print();
	}

	private static void genericCompile(File mainFile, String lang, Backend backend, String step) {
		org.kframework.kil.Definition javaDef;
		try {
			javaDef = org.kframework.utils.DefinitionLoader.loadDefinition(mainFile, lang);
			XStream xstream = new XStream();
			xstream.aliasPackage("k", "ro.uaic.info.fmse.k");

			String xml = xstream.toXML(javaDef);

			FileUtil.saveInFile(DefinitionHelper.dotk.getAbsolutePath() + "/defx.xml", xml);

			Stopwatch sw = new Stopwatch();

			CompilerSteps<Definition> steps = new CompilerSteps<Definition>();
			if (GlobalSettings.verbose) {
				steps.setSw(sw);
			}
			steps.add(new FirstStep());
			steps.add(new RemoveBrackets());
			steps.add(new AddEmptyLists());
			steps.add(new CheckVisitorStep(new CheckVariables()));
			steps.add(new CheckVisitorStep(new CheckRewrite()));
			steps.add(new AutomaticModuleImportsTransformer());
			steps.add(new StrictnessToContexts());
			steps.add(new FunctionalAdaptor(new DittoFilter()));
			steps.add(new FlattenModules());
			steps.add(new DesugarStreams());
			steps.add(new ResolveFunctions());
			steps.add(new AddKCell());
			steps.add(new AddSymbolicK());
			//			steps.add(new ResolveFresh());
			steps.add(new ResolveFreshMOS());
			if (GlobalSettings.addTopCell) {
				steps.add(new AddTopCell());
			}
			steps.add(new AddEval());
			steps.add(new ResolveBinder());
			steps.add(new ResolveAnonymousVariables());
			steps.add(new ResolveBlockingInput());
			steps.add(new AddK2SMTLib());
			steps.add(new AddPredicates());
			steps.add(new ResolveSyntaxPredicates());
			steps.add(new ResolveBuiltins());
			steps.add(new ResolveListOfK());
			steps.add(new FlattenSyntax());
			steps.add(new AddKLabelToString());
			steps.add(new AddKLabelConstant());
			steps.add(new ResolveHybrid());
			steps.add(new ResolveConfigurationAbstraction());
			steps.add(new ResolveOpenCells());
			steps.add(new ResolveRewrite());
			steps.add(new ResolveSupercool());
			steps.add(new AddStrictStar());
			steps.add(new AddDefaultComputational());
			steps.add(new AddOptionalTags());
			steps.add(new LastStep());
			
			if (step == null) {
				step = backend.getDefaultStep();
			}
			try {
				javaDef = steps.compile(javaDef, step);
			} catch (CompilerStepDone e) {
				javaDef = (Definition)e.getResult();
			}
			backend.run(javaDef);
		} catch (IOException e) {
			e.printStackTrace();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private static void lint(File mainFile, String mainModule) {
		try {
			File canonicalFile = mainFile.getCanonicalFile();
			org.kframework.kil.Definition javaDef = org.kframework.utils.DefinitionLoader.parseDefinition(canonicalFile, mainModule);

			KlintRule lintRule = new UnusedName(javaDef);
			lintRule.run();

			lintRule = new UnusedSyntax(javaDef);
			lintRule.run();

			lintRule = new InfiniteRewrite(javaDef);
			lintRule.run();
		} catch (IOException e1) {
			e1.printStackTrace();
		} catch (Exception e1) {
			e1.printStackTrace();
		}
	}

//	public static void pdfClean(String[] extensions) {
//		for (int i = 0; i < extensions.length; i++)
//			new File(GlobalSettings.mainFileWithNoExtension + extensions[i]).delete();
//	}

//	private static String html(File mainFile, String lang) {
//		org.kframework.kil.Definition javaDef;
//		try {
//
//			javaDef = org.kframework.utils.DefinitionLoader.loadDefinition(mainFile, lang);
//			// for now just use this file as main argument
//			File canonicalFile = mainFile.getCanonicalFile();
//
//			Stopwatch sw = new Stopwatch();
//			String fileSep = System.getProperty("file.separator");
//			String htmlIncludePath = KPaths.getKBase(false) + fileSep + "include" + fileSep + "html" + fileSep;
//			HTMLFilter htmlFilter = new HTMLFilter(htmlIncludePath);
//			javaDef.accept(htmlFilter);
//
//			String html = htmlFilter.getHTML();
//
//			FileUtil.saveInFile(DefinitionHelper.dotk.getAbsolutePath() + "/def.html", html);
//
//			FileUtil.saveInFile(FileUtil.stripExtension(canonicalFile.getAbsolutePath()) + ".html", html);
//
//			if (GlobalSettings.verbose) {
//				sw.printIntermediate("Html Generation");
//			}
//
//			return html;
//		} catch (IOException e1) {
//			e1.printStackTrace();
//		} catch (Exception e) {
//			e.printStackTrace();
//		}
//		return null;
//	}
//
//	private static String unparse(File mainFile, String lang) {
//		org.kframework.kil.Definition javaDef;
//		try {
//			javaDef = org.kframework.utils.DefinitionLoader.loadDefinition(mainFile, lang);
//			// for now just use this file as main argument
//			File canonicalFile = mainFile.getCanonicalFile();
//
//			Stopwatch sw = new Stopwatch();
//			UnparserFilter unparserFilter = new UnparserFilter();
//			javaDef.accept(unparserFilter);
//
//			String unparsedText = unparserFilter.getResult();
//
//			FileUtil.saveInFile(DefinitionHelper.dotk.getAbsolutePath() + "/def.k", unparsedText);
//
//			FileUtil.saveInFile(FileUtil.stripExtension(canonicalFile.getAbsolutePath()) + ".unparsed.k", unparsedText);
//
//			if (GlobalSettings.verbose) {
//				sw.printIntermediate("Unparsing");
//			}
//
//			return unparsedText;
//		} catch (IOException e1) {
//			e1.printStackTrace();
//		} catch (Exception e) {
//			e.printStackTrace();
//		}
//		return null;
//	}

//	public static String xml(File mainFile, String mainModule) {
//		try {
//			// for now just use this file as main argument
//			File canonicalFile = mainFile.getCanonicalFile();
//
//			// compile a definition here
//
//			org.kframework.kil.Definition javaDef = org.kframework.utils.DefinitionLoader.parseDefinition(canonicalFile, mainModule);
//
//			Stopwatch sw = new Stopwatch();
//			javaDef = (org.kframework.kil.Definition) javaDef.accept(new AddEmptyLists());
//
//			XStream xstream = new XStream();
//			xstream.aliasPackage("k", "ro.uaic.info.fmse.k");
//
//			String xml = xstream.toXML(javaDef);
//
//			FileUtil.saveInFile(DefinitionHelper.dotk.getAbsolutePath() + "/def.xml", xml);
//
//			FileUtil.saveInFile(canonicalFile.getAbsolutePath().replaceFirst("\\.k$", "") + ".xml", xml);
//
//			if (GlobalSettings.verbose) {
//				sw.printIntermediate("XML Generation");
//			}
//
//			return xml;
//		} catch (IOException e1) {
//			e1.printStackTrace();
//		} catch (Exception e) {
//			e.printStackTrace();
//		}
//		return null;
//	}

//	private static void fromxml(File xmlFile, String lang, String step) {
//		try {
//			// initial setup
//			File canoFile = xmlFile.getCanonicalFile();
//
//			// unmarshalling
//			XStream xstream = new XStream();
//			xstream.aliasPackage("k", "ro.uaic.info.fmse.k");
//
//			org.kframework.kil.Definition javaDef = (org.kframework.kil.Definition) xstream.fromXML(canoFile);
//			// This is essential for generating maude
//			javaDef.preprocess();
//
//			// javaDef = (ro.uaic.info.fmse.k.Definition) javaDef.accept(new AmbFilter());
//			// javaDef.accept(new CollectSubsortsVisitor());
//			// javaDef = (ro.uaic.info.fmse.k.Definition) javaDef.accept(new EmptyListsVisitor());
//
//			compile(javaDef, step);
//
//		} catch (IOException e) {
//			e.printStackTrace();
//		}
//	}

//	public static String maudify(File mainFile, String mainModule) {
//		try {
//			// compile a definition here
//			// for now just use this file as main argument
//			File f = mainFile.getCanonicalFile();
//
//			org.kframework.kil.Definition javaDef = org.kframework.utils.DefinitionLoader.parseDefinition(f, mainModule);
//
//			Stopwatch sw = new Stopwatch();
//
//			MaudeFilter maudeFilter = new MaudeFilter();
//			javaDef.accept(maudeFilter);
//
//			String maudified = maudeFilter.getResult();
//
//			FileUtil.saveInFile(DefinitionHelper.dotk.getAbsolutePath() + "/def.maude", maudified);
//
//			if (GlobalSettings.verbose) {
//				sw.printIntermediate("Maude Generation");
//			}
//
//			return maudified;
//		} catch (IOException e1) {
//			e1.printStackTrace();
//		} catch (Exception e) {
//			e.printStackTrace();
//		}
//		return null;
//	}
//
//	public static void compile(File mainFile, String mainModule, String step) {
//		try {
//			GlobalSettings.kem.print(KExceptionGroup.COMPILER);
//
//			// for now just use this file as main argument
//			File f = mainFile.getCanonicalFile();
//
//			org.kframework.kil.Definition javaDef = org.kframework.utils.DefinitionLoader.parseDefinition(f, mainModule);
//
//			Stopwatch sw = new Stopwatch();
//
//			MaudeFilter maudeFilter = new MaudeFilter();
//			javaDef.accept(maudeFilter);
//			FileUtil.saveInFile(DefinitionHelper.dotk.getAbsolutePath() + "/def.maude", maudeFilter.getResult());
//
//			XStream xstream = new XStream();
//			xstream.aliasPackage("k", "ro.uaic.info.fmse.k");
//
//			String xml = xstream.toXML(javaDef);
//
//			FileUtil.saveInFile(DefinitionHelper.dotk.getAbsolutePath() + "/defx.xml", xml);
//
//			if (GlobalSettings.verbose) {
//				sw.printIntermediate("Save in file");
//			}
//
//			compile(javaDef, step);
//		} catch (Exception e) {
//			e.printStackTrace();
//		}
//	}

//	public static void compile(org.kframework.kil.Definition javaDef, String step) {
//		// init stopwatch
//		Stopwatch sw = new Stopwatch();
//
//		CompilerSteps<Definition> steps = new CompilerSteps<Definition>();
//		if (GlobalSettings.verbose) {
//			steps.setSw(sw);
//		}
//		steps.add(new RemoveBrackets());
//		steps.add(new AddEmptyLists());
//		steps.add(new CheckVisitorStep(new CheckVariables()));
//		steps.add(new CheckVisitorStep(new CheckRewrite()));
//		steps.add(new AutomaticModuleImportsTransformer());
//		steps.add(new StrictnessToContexts());
//		steps.add(new FunctionalAdaptor(new DittoFilter()));
//		steps.add(new FlattenModules());
//		steps.add(new DesugarStreams());
//		steps.add(new ResolveFunctions());
//		steps.add(new AddKCell());
//		steps.add(new AddSymbolicK());
////			steps.add(new ResolveFresh());
//		steps.add(new ResolveFreshMOS());
//		if (GlobalSettings.addTopCell) {
//			steps.add(new AddTopCell());
//		}
//		steps.add(new AddEval());
//		steps.add(new ResolveBinder());
//		steps.add(new ResolveAnonymousVariables());
//		steps.add(new ResolveBlockingInput());
//		steps.add(new AddK2SMTLib());
//		steps.add(new AddPredicates());
//		steps.add(new ResolveSyntaxPredicates());
//		steps.add(new ResolveBuiltins());
//		steps.add(new ResolveListOfK());
//		steps.add(new FlattenSyntax());
//		steps.add(new AddKLabelToString());
//		steps.add(new AddKLabelConstant());
//		steps.add(new ResolveHybrid());
//		steps.add(new ResolveConfigurationAbstraction());
//		steps.add(new ResolveOpenCells());
//		steps.add(new ResolveRewrite());
//		steps.add(new ResolveSupercool());
//		steps.add(new AddStrictStar());
//		steps.add(new AddDefaultComputational());
//		steps.add(new AddOptionalTags());
//
//		try {
//			javaDef = steps.compile(javaDef, null);
//		} catch (CompilerStepDone e) {
//			javaDef = (Definition)e.getResult();
//		}
//		
//		String load = "load \"" + KPaths.getKBase(true) + "/bin/maude/lib/k-prelude\"\n";
//
//		// load libraries if any
//		String maudeLib = GlobalSettings.lib.equals("") ? "" : "load " + KPaths.windowfyPath(new File(GlobalSettings.lib).getAbsolutePath()) + "\n";
//		load += maudeLib;
//
//		String transition = metadataTags(GlobalSettings.transition);
//		String superheat = metadataTags(GlobalSettings.superheat);
//		String supercool = metadataTags(GlobalSettings.supercool);
//
//
//		MaudeFilter maudeFilter = new MaudeFilter();
//		javaDef.accept(maudeFilter);
//
//		String compile = load + maudeFilter.getResult() + " load \"" + KPaths.getKBase(true) + "/bin/maude/compiler/all-tools\"\n" + "---(\n" + "rew in COMPILE-ONESHOT : partialCompile('"
//				+ javaDef.getMainModule() + ", '" + step + ") .\n" + "quit\n" + "---)\n" + " loop compile .\n" + "(compile " + javaDef.getMainModule() + " " + step + " transitions " + transition
//				+ " superheats " + superheat + " supercools " + supercool + " anywheres \"anywhere=() function=() predicate=() macro=()\" "
//				+ "defineds \"function=() predicate=() defined=()\" .)\n" + "quit\n";
//
//		FileUtil.saveInFile(DefinitionHelper.dotk.getAbsolutePath() + "/compile.maude", compile);
//
//		if (GlobalSettings.verbose)
//			sw.printIntermediate("Generate Maude file");
//
//		// call maude to kompile the definition
//		String compiled = MaudeRun.run_maude(DefinitionHelper.dotk.getAbsoluteFile(), compile);
//
//		int start = compiled.indexOf("---K-MAUDE-GENERATED-OUTPUT-BEGIN---") + "---K-MAUDE-GENERATED-OUTPUT-BEGIN---".length();
//		int enddd = compiled.indexOf("---K-MAUDE-GENERATED-OUTPUT-END-----");
//		compiled = compiled.substring(start, enddd);
//
//		String defFile = javaDef.getMainFile().replaceFirst("\\.[a-zA-Z]+$", "");
//		FileUtil.saveInFile(defFile + "-compiled.maude", load + compiled);
//
//		if (start == -1 || enddd == -1) {
//			KException exception = new KException(ExceptionType.ERROR, KExceptionGroup.CRITICAL, "Incomplete output generated by the compiler. Check the '" + defFile + "-compiled.maude'.",
//					"top level", "Maude compilation");
//			GlobalSettings.kem.register(exception);
//		}
//
//		if (GlobalSettings.verbose)
//			sw.printIntermediate("RunMaude");
//	}

//	private static String metadataTags(List<String> tags) {
//		String result = "";
//		for (String s : tags) {
//			result += s + "=()";
//		}
//		return "\"" + result + "\"";
//	}
}
