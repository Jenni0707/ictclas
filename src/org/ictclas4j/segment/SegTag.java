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

	private int segPathCount = 1;// �ִ�·������Ŀ

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
		//����stop��
		Map<String,String> stopword = new HashMap<String,String>();  	// ���stopwords
	   String stoparray=", . ? '����ֻ���� ת�� ���� ����  �Ǹ� ��� ���� �� ���� ���� ��ô ���� û�� �Ѿ� һ�� ���� һ�� ����  ��ʱ ��Ϊ ���� Ҳ�� ���� ��ס ���� ���� һ�� ͼƬ ���� ���� ��֪���� ��� ���� ÿ�� һ��  �� �� �� �� �� �� �� ���  ���� ��� ���� ���  ����  ���� ���� ��� ���� ���� ���� һ�� ��ȥ ���� ���� ���� һ�� ֪�� û�� ��Ҫ ��Ҫ ʱ�� ��Ϊ ��� ���� ���� ÿ�� ��Ϊ`^~<=>|_- --,;:!?/. ...'\"()@$*\\&#%+ֱ��ֱ��ֻ��ֻ��ֻ�µǷֱ��ִ��ֻ��ֵȱ�����վ������ǣ��������롵���ݺ��ã��������ؾ����գ��˳�������˿Ȱ������ǳ����ر��������߼������ˣ����ۡ���������֮������ƾ�ɰ����������ڣ��������Ұ�������������𣰡��������ţ���ɶ��������������˳�ϵأ�������Ρ��������ۣ������ԣ����������۰ɵ������ڰ��Ծ��������´�����֨������ ";
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
		SegResult sr = new SegResult(src);// �ִʽ��
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
					// ԭ�ӷִ�
					AtomSeg as = new AtomSeg(sen.getContent());
					ArrayList<Atom> atoms = as.getAtoms();
					mr.setAtoms(atoms); 
					println2Err("[atom time]:"+(System.currentTimeMillis()-start));
					start=System.currentTimeMillis();
					
					// ���ɷִ�ͼ��,�Ƚ��г����ִʣ�Ȼ������Ż��������д��Ա��
					SegGraph segGraph = GraphGenerate.generate(atoms, coreDict);
					mr.setSegGraph(segGraph.getSnList());
					// ���ɶ���ִ�ͼ��
					SegGraph biSegGraph = GraphGenerate.biGenerate(segGraph, coreDict, bigramDict);
					mr.setBiSegGraph(biSegGraph.getSnList());
					println2Err("[graph time]:"+(System.currentTimeMillis()-start));
					start=System.currentTimeMillis();
					
					// ��N���·��
					NShortPath nsp = new NShortPath(biSegGraph, segPathCount);
					ArrayList<ArrayList<Integer>> bipath = nsp.getPaths();
					mr.setBipath(bipath);
					println2Err("[NSP time]:"+(System.currentTimeMillis()-start));
					start=System.currentTimeMillis();
					
					for (ArrayList<Integer> onePath : bipath) {
						// �õ����ηִ�·��
						ArrayList<SegNode> segPath = getSegPath(segGraph, onePath);
						ArrayList<SegNode> firstPath = AdjustSeg.firstAdjust(segPath);
						String firstResult = outputResult(firstPath);
						mr.addFirstResult(firstResult);
						println2Err("[first time]:"+(System.currentTimeMillis()-start));
						start=System.currentTimeMillis();

						// ����δ��½�ʣ����Գ��ηִʽ�������Ż�
						SegGraph optSegGraph = new SegGraph(firstPath);
						ArrayList<SegNode> sns = clone(firstPath);
						personTagger.recognition(optSegGraph, sns);
						transPersonTagger.recognition(optSegGraph, sns);
						placeTagger.recognition(optSegGraph, sns);
						mr.setOptSegGraph(optSegGraph.getSnList());
						println2Err("[unknown time]:"+(System.currentTimeMillis()-start));
						start=System.currentTimeMillis();

						// �����Ż���Ľ�������½������ɶ���ִ�ͼ��
						SegGraph optBiSegGraph = GraphGenerate.biGenerate(optSegGraph, coreDict, bigramDict);
						mr.setOptBiSegGraph(optBiSegGraph.getSnList());

						// ������ȡN�����·��
						NShortPath optNsp = new NShortPath(optBiSegGraph, segPathCount);
						ArrayList<ArrayList<Integer>> optBipath = optNsp.getPaths();
						mr.setOptBipath(optBipath);

						// �����Ż���ķִʽ�������Խ�����д��Ա�Ǻ������Ż���������
						ArrayList<SegNode> adjResult = null;
						for (ArrayList<Integer> optOnePath : optBipath) {
							ArrayList<SegNode> optSegPath = getSegPath(optSegGraph, optOnePath);
							lexTagger.recognition(optSegPath);   //��ע1
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
				
					
				//ȥ��ע
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
				    //��һ���ո��滻����ĸ
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
					
					else  //add����
					{
					 
						sb.append(midResult.charAt(cur));
					    chinese=true; 
					    //��ʱ�������ָ���
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

	// ���ݶ���ִ�·�����ɷִ�·��
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

	// ���ݷִ�·�����ɷִʽ��
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
		  String file_in="test����.txt";
         FileInputStream fis = new FileInputStream(file_in);
		 BufferedReader reader= new BufferedReader(new InputStreamReader(fis,"GBK"));
		String line=null;
			while ((line=reader.readLine())!=null) {
		
			try {
				if(line!=" "&&!(line.contains("�۸�")||line.contains("����")||line.contains("����")||line.contains("�û�")||line.contains("������")||line.contains("����ͼƬ")||line.contains("ѫ��")||line.contains("����")||line.contains("��������")||line.contains("��ʼ��Ϸ")||line.contains("��֮���װ�")||line.contains("һ�ָ���")||line.contains("�����ս")||line.contains("��������")))//ȥ����΢�� 
				{
					SegResult seg_res=segTag.split(line);//�ִ�
					String str=seg_res.toString();
					
					//write result to file
					//׷��д���ļ�
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
			      } //ȥ����΢��   
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
