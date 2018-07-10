/*
 * Copyright (c) 2010-2018, sikuli.org, sikulix.com - MIT license
 */
package org.sikuli.script;

import org.sikuli.basics.Settings;
import org.sikuli.basics.Debug;
import java.awt.image.BufferedImage;
import java.util.Iterator;
import org.sikuli.natives.finder.FindInput;
import org.sikuli.natives.finder.FindResult;
import org.sikuli.natives.finder.FindResults;
import org.sikuli.natives.finder.Vision;
import org.sikuli.natives.finder.Mat;

public class Finder implements Iterator<Match> {

  public final static int TARGET_TYPE_TEXT = 3;
  static RunTime runTime = RunTime.get();

  private Region _region = null;
  private Pattern _pattern = null;
  private Image _image = null;
  private FindInput _findInput = null;
  private FindResults _results = null;
  private int _cur_result_i;
  private boolean repeating = false;
  private boolean valid = true;
  private boolean screenFinder = true;

  static {
    //TODO RunTime.loadLibrary("VisionProxy");
  }

  private static String me = "Finder: ";
  private static int lvl = 3;
  private static void log(int level, String message, Object... args) {
    Debug.logx(level, me + message, args);
  }

//<editor-fold defaultstate="collapsed" desc="Constructors">
  /**
   * Just to force library initialization
   */
  private Finder() {}

  /**
   * Finder constructor (finding within an image).
   * <br>internally used with a screen snapshot
   *
   * @param imageFilename a string (name, path, url)
   * @throws java.io.IOException if imagefile not found
   */
//  public Finder(String imageFilename) throws IOException {
//    this(imageFilename, null);
//  }

  /**
   * Finder constructor (finding within an image within the given region).
   * <br>internally used with a screen snapshot
   *
   * @param imageFilename a string (name, path, url)
   * @param region search Region within image - topleft = (0,0)
   * @throws java.io.IOException if imagefile not found
   */
//  public Finder(String imageFilename, Region region) throws IOException  {
//    Image img = Image.create(imageFilename);
//    if (img.isValid()) {
//      _findInput.setSource(Image.convertBufferedImageToMat(img.get()));
//      _region = region;
//      screenFinder = false;
//    } else {
//      log(-1, "imagefile not found:\n%s", imageFilename);
//      valid = false;
//    }
//  }

  /**
   * Constructor for special use from a BufferedImage
   *
   * @param bimg BufferedImage
   */
//  public Finder(BufferedImage bimg) {
//    _findInput.setSource(Image.convertBufferedImageToMat(bimg));
//  }

  /**
   * Finder constructor for special use from a ScreenImage
   *
   * @param simg ScreenImage
   */
//  public Finder(ScreenImage simg) {
//    initScreenFinder(simg, null);
//  }

  /**
   * Finder constructor for special use from a ScreenImage
   *
   * @param simg ScreenImage
   * @param region the cropping region
   */
  public Finder(ScreenImage simg, Region region) {
    initScreenFinder(simg, region);
  }

  /**
   * Finder constructor for special use from an Image
   *
   * @param img Image
   */
//  public Finder(Image img) {
//    log(lvl, "Image: %s", img);
//    _findInput.setSource(Image.convertBufferedImageToMat(img.get()));
//  }

  public void resetImage(Image img) {
    _findInput.setSource(Image.convertBufferedImageToMat(img.get()));
  }

  private void initScreenFinder(ScreenImage simg, Region region) {
    _findInput = new FindInput();
    setScreenImage(simg);
    _region = region;
  }

  /**
   * internal use: exchange the source image in existing Finder
   *
   * @param simg ScreenImage
   */
  protected void setScreenImage(ScreenImage simg) {
    _findInput.setSource(Image.convertBufferedImageToMat(simg.getImage()));
  }

  /**
   * to explicitly free the Finder's resources
   */
  public void destroy() {
    _findInput.delete();
    _findInput = null;
    if (_results != null) {
      _results.delete();
      _results = null;
    }
    _pattern = null;
  }

  /**
   * not used
   */
  @Override
  public void remove(){}

  @Override
  protected void finalize() throws Throwable {
    super.finalize();
    destroy();
  }

//  public boolean isValid() {
//    return valid;
//  }
  //</editor-fold>

//<editor-fold defaultstate="collapsed" desc="internal repeating">
  /**
   * internal use: to be able to reuse the same Finder
   */
  protected void setRepeating() {
    repeating = true;
  }

  /**
   * internal use: repeat with same Finder
   */
  protected void findRepeat() {
    _results = Vision.find(_findInput);
    _cur_result_i = 0;
  }

  /**
   * internal use: repeat with same Finder
   */
  protected void findAllRepeat() {
    Debug timing = Debug.startTimer("Finder.findAll");
    _results = Vision.find(_findInput);
    _cur_result_i = 0;
    timing.end();
  }
//</editor-fold>

//<editor-fold defaultstate="collapsed" desc="find">

  /**
   * do a find op with the given image or the given text in the Finder's image
   * (hasNext() and next() will reveal possible match results)
   * @param imageOrText image file name or text
   * @return null. if find setup not possible
   */
  public String find(String imageOrText) {
    if (!valid) {
      log(-1, "not valid");
      return null;
    }
    Image img = Image.create(imageOrText);
    if (img.isText()) {
      return findText(imageOrText);
    }
    if (img.isValid()) {
      return find(img);
    }
    return null;
  }

  private Mat possibleImageResizeOrCallback(Image img) {
    return possibleImageResizeOrCallback(img, 0);
  }

  private Mat possibleImageResizeOrCallback(Image img, float oneTimeResize) {
    BufferedImage newBimg = img.get();
    float factor = oneTimeResize;
    if (factor == 0 && Settings.AlwaysResize > 0 && Settings.AlwaysResize != 1) {
      factor = Settings.AlwaysResize;
    }
    if (factor > 0 && factor != 1) {
      Debug.log(3, "Finder::possibleImageResizeOrCallback: resize");
      newBimg = Image.resize(newBimg, factor);
    } else if (Settings.ImageCallback != null) {
      Debug.log(3, "Finder::possibleImageResizeOrCallback: callback");
      newBimg = Settings.ImageCallback.callback(img);
    }
    return Image.convertBufferedImageToMat(newBimg);
  }

  /**
   * do a find op with the given pattern in the Finder's image
   * (hasNext() and next() will reveal possible match results)
   * @param aPtn Pattern
   * @return null. if find setup not possible
   */
  public String find(Pattern aPtn) {
    if (!valid) {
      log(-1, "not valid");
      return null;
    }
    if (aPtn.isValid()) {
      _pattern = aPtn;
      _image = aPtn.getImage();
      _findInput.setTarget(possibleImageResizeOrCallback(_image, aPtn.getResize()));
      _findInput.setSimilarity(aPtn.getSimilar());
      _results = Vision.find(_findInput);
      _cur_result_i = 0;
      return aPtn.getFilename();
    } else {
      return null;
    }
  }

  /**
   * do a find op with the given pattern in the Finder's image
   * (hasNext() and next() will reveal possible match results)
   * @param img Image
   * @return null. if find setup not possible
   */
  public String find(Image img) {
    if (!valid) {
      log(-1, "not valid");
      return null;
    }
    if (img.isValid()) {
      _image = img;
      _findInput.setTarget(possibleImageResizeOrCallback(img));
      _findInput.setSimilarity(Settings.MinSimilarity);
      _results = Vision.find(_findInput);
      _cur_result_i = 0;
      return img.getFilename();
    } else if (img.isUseable()) {
      return find(new Pattern(img));
    } else {
      return null;
    }
  }

  /**
   * do a text find with the given text in the Finder's image
   * (hasNext() and next() will reveal possible match results)
   * @param text text
   * @return null. if find setup not possible
   */
  public String findText(String text) {
    if (!valid) {
      log(-1, "not valid");
      return null;
    }
    _findInput.setTarget(TARGET_TYPE_TEXT, text);
    _results = Vision.find(_findInput);
    _cur_result_i = 0;
    return text;
  }
//</editor-fold>

//<editor-fold defaultstate="collapsed" desc="findAll">
  /**
   * do a findAll op with the given image or the given text in the Finder's image
   * (hasNext() and next() will reveal possible match results)
   * @param imageOrText iamge file name or text
   * @return null. if find setup not possible
   */
  public String findAll(String imageOrText) {
    if (!valid) {
      log(-1, "not valid");
      return null;
    }
    Image img = Image.create(imageOrText);
    _image = img;
    if (img.isText()) {
      return findAllText(imageOrText);
    }
    if (img.isValid()) {
      return findAll(img);
    }
    return null;
  }

  /**
   * do a find op with the given pattern in the Finder's image
   * (hasNext() and next() will reveal possible match results)
   * @param aPtn Pattern
   * @return null. if find setup not possible
   */
  public String findAll(Pattern aPtn)  {
    if (!valid) {
      log(-1, "not valid");
      return null;
    }
    if (aPtn.isValid()) {
      _pattern = aPtn;
      _image = aPtn.getImage();
      _findInput.setTarget(possibleImageResizeOrCallback(_image, aPtn.getResize()));
      _findInput.setSimilarity(aPtn.getSimilar());
      _findInput.setFindAll(true);
      Debug timing = Debug.startTimer("Finder.findAll");
      _results = Vision.find(_findInput);
      _cur_result_i = 0;
      timing.end();
      return aPtn.getFilename();
    } else {
      return null;
    }
  }

  /**
   * do a findAll op with the given image in the Finder's image
   * (hasNext() and next() will reveal possible match results)
   * @param img Image
   * @return null. if find setup not possible
   */
  public String findAll(Image img)  {
    if (!valid) {
      log(-1, "not valid");
      return null;
    }
    if (img.isValid()) {
      _image = img;
      _findInput.setTarget(possibleImageResizeOrCallback(img));
      _findInput.setSimilarity(Settings.MinSimilarity);
      _findInput.setFindAll(true);
      Debug timing = Debug.startTimer("Finder.findAll");
      _results = Vision.find(_findInput);
      _cur_result_i = 0;
      timing.end();
      return img.getFilename();
    } else {
      return null;
    }
  }

  /**
   * do a findAll op with the given text in the Finder's image
   * (hasNext() and next() will reveal possible match results)
   * @param text text
   * @return null. if find setup not possible
   */
  public String findAllText(String text) {
    if (!valid) {
      log(-1, "not valid");
      return null;
    }
    _findInput.setTarget(TARGET_TYPE_TEXT, text);
    _findInput.setFindAll(true);
    Debug timing = Debug.startTimer("Finder.findAllText");
    _results = Vision.find(_findInput);
    _cur_result_i = 0;
    timing.end();
    return text;
  }
//</editor-fold>

//  private String setTargetSmartly(FindInput fin, String target) {
//    if (isImageFile(target)) {
//      //assume it's a file first
//      String filename = Image.create(target).getFilename();
//      if (filename != null) {
//        fin.setTarget(TARGET_TYPE.IMAGE, filename);
//        return filename;
//      } else {
//        if (!repeating) {
//          Debug.error(target
//                  + " looks like a file, but not on disk. Assume it's text.");
//        }
//      }
//    }
//    if (!Settings.OcrTextSearch) {
//      Debug.error("Region.find(text): text search is currently switched off");
//      return target + "???";
//    } else {
//      fin.setTarget(TARGET_TYPE.TEXT, target);
//      if (TextRecognizer.getInstance() == null) {
//        Debug.error("Region.find(text): text search is now switched off");
//        return target + "???";
//      }
//      return target;
//    }
//  }

//	private static boolean isImageFile(String fname) {
//		int dot = fname.lastIndexOf('.');
//		if (dot < 0) {
//			return false;
//		}
//		String suffix = fname.substring(dot + 1).toLowerCase();
//		if (suffix.equals("png") || suffix.equals("jpg")) {
//			return true;
//		}
//		return false;
//	}

  /**
   *
   * @return true if Finder has a next match, false otherwise
   */
  @Override
  public boolean hasNext() {
    if (_results != null && _results.size() > _cur_result_i) {
      return true;
    }
    return false;
  }

  /**
   *
   * @return the next match or null
   */
  @Override
  public Match next() {
    Match match = null;
    if (hasNext()) {
      FindResult fr = _results.get(_cur_result_i++);
      IScreen parentScreen = null;
      if (screenFinder && _region != null) {
        parentScreen = _region.getScreen();
      }
      match = new Match(fr, parentScreen);
      match.setOnScreen(screenFinder);
			fr.delete();
      if (_region != null) {
        match = _region.toGlobalCoord(match);
      }
      if (_pattern != null) {
        Location offset = _pattern.getTargetOffset();
        match.setTargetOffset(offset);
      }
      match.setImage(_image);
    }
    return match;
  }
}
