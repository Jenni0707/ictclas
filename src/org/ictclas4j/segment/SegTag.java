package org.ictclas4j.segment;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import org.ictclas4j.bean.Atom;
import org.ictclas4j.bean.Dictionary;
import org.ictclas4j.bean.MidResult;
import org.ictclas4j.bean.SegNode;
import org.ictclas4j.bean.SegResult;
import org.ictclas4j.bean.Sentence;
import org.ictclas4j.utility.POSTag;
import org.ictclas4j.utility.Utility;

public class SegTag {
	private Dictionary coreDict;
	private Dictionary bigramDict;
	private PosTagger personTagger;
	private PosTagger transPersonTagger;
	private PosTagger placeTagger;
	private PosTagger lexTagger;

	private int segPathCount = 1;// 分词路径的数目

	public SegTag(int segPathCount) {
		this.segPathCount = segPathCount;
		coreDict = new Dictionary("data\\coreDict.dct");

		bigramDict = new Dictionary("data\\bigramDict.dct");
		personTagger = new PosTagger(Utility.TAG_TYPE.TT_PERSON, "data\\nr", coreDict);
		transPersonTagger = new PosTagger(Utility.TAG_TYPE.TT_TRANS_PERSON, "data\\tr", coreDict);
		placeTagger = new PosTagger(Utility.TAG_TYPE.TT_TRANS_PERSON, "data\\ns", coreDict);
		lexTagger = new PosTagger(Utility.TAG_TYPE.TT_NORMAL, "data\\lexical", coreDict);
	}

	public SegResult split(String src) throws FileNotFoundException {
		//读入stop词
		Map<String,String> stopword = new HashMap<String,String>();  	// 存放stopwords
	   String stoparray=", . ? '，。只不过 转发 及其 最终  那个 这个 俺们 不 你们 我们 那么 及其 没有 已经 一个 就是 一阵 哈哈  当时 以为 嘻嘻 也好 各种 记住 终于 下载 一起 图片 与其 不如 不知不觉 大家 还有 每个 一举  的 是 你 我 他 这 那 获得  懂得 大家 容易 面对  害怕  所有 这样 最后 觉得 哈哈 嘻嘻 一定 下去 我们 他们 你们 一个 知道 没有 需要 不要 时候 因为 如何 不能 觉得 每天 认为`^~<=>|_- --,;:!?/. ...'\"()@$*\\&#%+直到直接只限只是只嘎登分别现代抑或又等比其嘿照经乃啐呵｝６１＄离〉怎］乎用８～＃的呜就嗳哉＊彼趁喏、︿此咳八啦们那朝管沿本归呃宁者几到嘛咚５五哇“＜所唉因之个二别’凭由啊＋拿嗯有在）哪连哼且俺您｜你向这和起０〈往她依九；任啥—哎得焉七随呸顺较地｛借像４则何》纵另？各［＆‘哈自！阿矣零能咱吧当哩（于按对据若边三嘎打它：吱而％呢 ";
		/*	FileInputStream fis = new FileInputStream("Data\\stop_little.txt");
			
				BufferedReader reader;
				try {
					reader = new BufferedReader(new InputStreamReader(fis,"GBK"));
				
			
			String line=null;
			try {
				while((line = reader.readLine())!= null){
					String stop = line;
				//	System.out.println(stop);
					if(stopword.get(stop)==null)
					{
						stopword.put(stop," ");
					}
				}
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
				} catch (UnsupportedEncodingException e1) {
					// TODO Auto-generated catch block
					e1.printStackTrace();
				}
				*/
		SegResult sr = new SegResult(src);// 分词结果
		String finalResult = null;

		if (src != null) {
			finalResult = "";
			int index = 0;
			String midResult = null;
			sr.setRawContent(src);
			SentenceSeg ss = new SentenceSeg(src);
			ArrayList<Sentence> sens = ss.getSens();
			
			for (Sentence sen : sens) {
				long start=System.currentTimeMillis();
				MidResult mr = new MidResult();
				mr.setIndex(index++);
				mr.setSource(sen.getContent());
				if (sen.isSeg()) {
					// 原子分词
					AtomSeg as = new AtomSeg(sen.getContent());
					ArrayList<Atom> atoms = as.getAtoms();
					mr.setAtoms(atoms); 
					println2Err("[atom time]:"+(System.currentTimeMillis()-start));
					start=System.currentTimeMillis();
					
					// 生成分词图表,先进行初步分词，然后进行优化，最后进行词性标记
					SegGraph segGraph = GraphGenerate.generate(atoms, coreDict);
					mr.setSegGraph(segGraph.getSnList());
					// 生成二叉分词图表
					SegGraph biSegGraph = GraphGenerate.biGenerate(segGraph, coreDict, bigramDict);
					mr.setBiSegGraph(biSegGraph.getSnList());
					println2Err("[graph time]:"+(System.currentTimeMillis()-start));
					start=System.currentTimeMillis();
					
					// 求N最短路径
					NShortPath nsp = new NShortPath(biSegGraph, segPathCount);
					ArrayList<ArrayList<Integer>> bipath = nsp.getPaths();
					mr.setBipath(bipath);
					println2Err("[NSP time]:"+(System.currentTimeMillis()-start));
					start=System.currentTimeMillis();
					
					for (ArrayList<Integer> onePath : bipath) {
						// 得到初次分词路径
						ArrayList<SegNode> segPath = getSegPath(segGraph, onePath);
						ArrayList<SegNode> firstPath = AdjustSeg.firstAdjust(segPath);
						String firstResult = outputResult(firstPath);
						mr.addFirstResult(firstResult);
						println2Err("[first time]:"+(System.currentTimeMillis()-start));
						start=System.currentTimeMillis();

						// 处理未登陆词，进对初次分词结果进行优化
						SegGraph optSegGraph = new SegGraph(firstPath);
						ArrayList<SegNode> sns = clone(firstPath);
						personTagger.recognition(optSegGraph, sns);
						transPersonTagger.recognition(optSegGraph, sns);
						placeTagger.recognition(optSegGraph, sns);
						mr.setOptSegGraph(optSegGraph.getSnList());
						println2Err("[unknown time]:"+(System.currentTimeMillis()-start));
						start=System.currentTimeMillis();

						// 根据优化后的结果，重新进行生成二叉分词图表
						SegGraph optBiSegGraph = GraphGenerate.biGenerate(optSegGraph, coreDict, bigramDict);
						mr.setOptBiSegGraph(optBiSegGraph.getSnList());

						// 重新求取N－最短路径
						NShortPath optNsp = new NShortPath(optBiSegGraph, segPathCount);
						ArrayList<ArrayList<Integer>> optBipath = optNsp.getPaths();
						mr.setOptBipath(optBipath);

						// 生成优化后的分词结果，并对结果进行词性标记和最后的优化调整处理
						ArrayList<SegNode> adjResult = null;
						for (ArrayList<Integer> optOnePath : optBipath) {
							ArrayList<SegNode> optSegPath = getSegPath(optSegGraph, optOnePath);
							lexTagger.recognition(optSegPath);   //标注1
							String optResult = outputResult(optSegPath);
							mr.addOptResult(optResult);
							adjResult = AdjustSeg.finaAdjust(optSegPath, personTagger, placeTagger);
							String adjrs = outputResult(adjResult);
							println2Err("[last time]:"+(System.currentTimeMillis()-start));
							start=System.currentTimeMillis();
							if (midResult == null)
								midResult = adjrs;
							break;
						}
					}
					sr.addMidResult(mr);
				} else
					midResult = sen.getContent();
				
					
				//去标注
			/*			String tmp,mid;
						tmp="";mid="";
						
				 StringBuffer sb=new StringBuffer();
			     boolean space=false;
			     boolean chinese=false;
			   
				 for(int cur=0;cur<midResult.length();cur++)
				 {
					//stopword clear
					   boolean stopcontain=false;
						if(midResult.charAt(cur)=='/')
						{
							mid=sb.toString();
							sb=new StringBuffer();
						//	System.out.println(mid);
							//if(stopword.get(mid)==null)
							if(stoparray.contains(mid)==false&&mid.length()>=3)
							{
								tmp+=mid;
								tmp+='/';
							}
							else
							{
								stopcontain=true;
							}
						}
				    //用一个空格替换掉字母
					if((midResult.charAt(cur)>='a'&&midResult.charAt(cur)<='z')||midResult.charAt(cur)=='/'
						||midResult.charAt(cur)=='<'||midResult.charAt(cur)=='>')
					{
						
					 
					  if(space==false)
					   {
					     // sb.append(" ");
		//				  sb.append("/");
					   }
					    chinese=false;
					    space=true;
					  
					 }
					
					else  //add汉字
					{
					 
						sb.append(midResult.charAt(cur));
					    chinese=true; 
					    //把时间与文字隔开
						 // if(cur+3<mid.length()&&midResult.charAt(cur)=='t'&&midResult.charAt(cur+1)=='i'&&midResult.charAt(cur+2)=='m'&&midResult.charAt(cur+3)=='e')
						   if(cur==mid.indexOf(':')-5)
							{
								System.out.println("cur:"+cur);	
								System.out.println(":"+mid.indexOf(':'));	
								//sb.append(midResult.charAt(cur));
								sb.append('/');	
										
							}
				    }
					
				 }//for	
				 
				
				  // tmp +="/";
				   finalResult +=tmp;
				  
			*/
			 finalResult += midResult;
				  midResult = null;
			}

			sr.setFinalResult(finalResult);
		}
   System.out.println(finalResult.toString());
		return sr;
	}

	private ArrayList<SegNode> clone(ArrayList<SegNode> sns) {
		ArrayList<SegNode> result = null;
		if (sns != null && sns.size() > 0) {
			result = new ArrayList<SegNode>();
			for (SegNode sn : sns)
				result.add(sn.clone());
		}
		return result;
	}

	// 根据二叉分词路径生成分词路径
	private ArrayList<SegNode> getSegPath(SegGraph sg, ArrayList<Integer> bipath) {
		ArrayList<SegNode> path = null;

		if (sg != null && bipath != null) {
			ArrayList<SegNode> sns = sg.getSnList();
			path = new ArrayList<SegNode>();

			for (int index : bipath)
				path.add(sns.get(index));
		}
		return path;
	}

	// 根据分词路径生成分词结果
	private String outputResult(ArrayList<SegNode> wrList) {
		String result = null;
		String temp=null;
		char[] pos = new char[2];
		if (wrList != null && wrList.size() > 0) {
			result = "";
			for (int i = 0; i < wrList.size(); i++) {
				SegNode sn = wrList.get(i);
				if (sn.getPos() != POSTag.SEN_BEGIN && sn.getPos() != POSTag.SEN_END) {
					int tag = Math.abs(sn.getPos());
					pos[0] = (char) (tag / 256);
					pos[1] = (char) (tag % 256);
					temp=""+pos[0];
					if(pos[1]>0)
						temp+=""+pos[1];
					result += sn.getSrcWord() + "/" + temp + " ";
				}
			}
		}
 // System.out.println(result);
		return result;
	}

	public void setSegPathCount(int segPathCount) {
		this.segPathCount = segPathCount;
	}
	
	public static void main(String[] args) throws FileNotFoundException, UnsupportedEncodingException{
		SegTag segTag = new SegTag(1);	
		System.out.println("good");
		
		try {
		// duo files
	for(int f=1;f<5;f++)
	 {
		  //String file_in="D:\\DownloadData\\timeline\\timeline3.11-\\/hour"+f+".txt";
		  String file_in="test测试.txt";
         FileInputStream fis = new FileInputStream(file_in);
		 BufferedReader reader= new BufferedReader(new InputStreamReader(fis,"GBK"));
		String line=null;
			while ((line=reader.readLine())!=null) {
		
			try {
				if(line!=" "&&!(line.contains("价格")||line.contains("乱世")||line.contains("星座")||line.contains("用户")||line.contains("给力了")||line.contains("分享图片")||line.contains("勋章")||line.contains("三国")||line.contains("我在这里")||line.contains("开始游戏")||line.contains("来之不易啊")||line.contains("一分耕耘")||line.contains("你敢挑战")||line.contains("我是信了")))//去噪声微博 
				{
					SegResult seg_res=segTag.split(line);//分词
					String str=seg_res.toString();
					
					//write result to file
					//追加写入文件
				/*	String fpath="test\\timeline_taged_pos_byhour3.11\\tag_result_dic"+f+".txt";
					String tmp=seg_res.getFinalResult();
					tmp+="\n";
         			  BufferedWriter out = null;   
         		    try { 
         		    	
         		         out = new BufferedWriter(new OutputStreamWriter(   
         		                  new FileOutputStream(fpath, true),"GBK"));   
         		               out.write(tmp); 
         	 
	                    } catch (Exception e) {   
         		            e.printStackTrace();   
         		        } 
	                finally {   
         		            try {   
         		                  out.close();   
         		                } catch (IOException e) 
         		                   {   
         		                      e.printStackTrace();   
         		                    }
         		           }//finally    save to file
         		       */
			      } //去噪声微博   
				} catch (Throwable t) {
					t.printStackTrace();					
				}			
		}  //while
	       }// duo files
		} catch (IOException e) {			
			e.printStackTrace();
		}						
	}//main
	
	private static void println2Err(String str) {
		//System.err.println(str);		
	}
}
