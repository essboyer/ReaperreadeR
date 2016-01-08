/**************************** ReaperreadeR.java ********************************
 * Reads in a Reaper project file and writes out all sorts of info, mainly about
 * the tracks. My first use of this is to find the diff between two versions of
 * the same project. Somewhere along the line I messed up parts of Gotta Go, and
 * now want to find out what the difference is between the earlier versions and
 * the later.
 */
package reaper.reader;

import java.io.StreamTokenizer;
//------------------------------
class Sends
{
	
}
//-------------------------------------
class VST
{
	private final String name;
	private final String dll;
	private final int bypass;
	
	public VST(final ReaperTokenizer reader) throws java.io.IOException
	{
		assert reader.getWord().equals("VST") : "Expected VST as current token (Track)";
		reader.nextToken();	// Should be the name
		this.name = reader.getWord();
		reader.nextToken();	// Should be the dll
		this.dll = reader.getWord();
		reader.nextToken();	// Should be the bypass state (should it?)
		assert reader.getToken() == ReaperTokenizer.Tokens.NUMBER : "Expected NUMBER as current token (VST)";
		this.bypass = (int)Double.parseDouble(reader.getWord());
	}
	
	@Override
	public String toString()
	{
		StringBuffer buf = new StringBuffer();
		buf.append(this.name).append(" [").append(this.dll).append("] ");
		if(this.bypass == 0) buf.append("bypassed");
			
		return buf.toString();
	}
	
}
//----------------------------------
class Track
{
	private final ReaperTokenizer reader;
	private final int track_num;
	private final String UUID;
	private final String name;
//	private final int volume;
//	private final int pan;
//	private final int wide;
	private final int mute;
	private final int solo;
	private final java.util.List<VST> fx_list = new java.util.LinkedList<>();
	private final java.util.List<Sends> sends_list = new java.util.LinkedList<>();
	
	public Track(final int num, final ReaperTokenizer reader) throws java.io.IOException
	{
		this.reader = reader;

		assert this.reader.getWord().equals("TRACK") : "Expected TRACK as current token";
		
		this.track_num = num;

		this.reader.nextToken();	// Should be the UUID
		this.UUID = this.reader.getWord();
		this.reader.nextToken();	// Should be "NAME";
		
		assert this.reader.getWord().equals("NAME") : "Expected NAME as current token (Track)";
		
		this.reader.nextToken();	// Should be the name string, may be empty
		this.name = this.reader.getWord();
		
		if(this.skipToMuteSolo())
		{
			assert this.reader.getWord().equals("MUTESOLO") : "Expected MUTESOLO as current token (Track)";
			this.reader.nextToken(); 
			assert this.reader.getToken() == ReaperTokenizer.Tokens.NUMBER : "Expected NUMBER as current token (mute)";
			this.mute = (int)Double.parseDouble(this.reader.getWord());
			this.reader.nextToken();
			assert this.reader.getToken() == ReaperTokenizer.Tokens.NUMBER : "Expected NUMBER as current token (solo)";
			this.solo = (int)Double.parseDouble(this.reader.getWord());
		}
		else
		{
			this.mute = 0;
			this.solo = 0;
		}
		
//		if(skipToFXchain())	// This should always be there, whether it is empty or not.
//		{
//			
//		}
		while(this.skipToVST())
		{
			VST vst = new VST(reader);
			this.fx_list.add(vst);
		}

	}
	
	@Override
	public String toString()
	{
		final StringBuffer buf = new StringBuffer();
		buf.append("Track ");
		buf.append(this.track_num);
		buf.append(": ");
		
		final String name = this.name.isEmpty() ? "anonymous" : this.name;
		buf.append(name);
		
		buf.append(", mute:").append(this.mute);
		buf.append(", solo:").append(this.solo);
		
		if(this.fx_list.isEmpty())
			buf.append("\n\tNo FX on track");
		else
		{
			for(final VST vst : this.fx_list)
			{ 
				buf.append("\n\t").append(vst.toString());
			}
		}
		
		return buf.toString();
	}
	
	private boolean skipToMuteSolo() throws java.io.IOException
	{
//		while(this.reader.nextToken() != Tokens.EOF)
//		{
//			if(this.reader.getWord().equals("MUTESOLO"))
//				return true;
//		}
//		return false;
		return this.reader.skipTo("MUTESOLO") != ReaperTokenizer.Tokens.EOF;
	}
	
	private boolean skipToFXchain() throws java.io.IOException
	{
		return this.reader.skipTo("FXCHAIN") != ReaperTokenizer.Tokens.EOF;
	}
	
	/*
	 * Here we must be careful. Though every track has an FXCHAIN entry, it 
	 * might be empty, so we cannot skip to VST without risking overrunning to
	 * the next track. So we must skip to both. Note that there are only two
	 * levels in a Reaper project file (actually 3, but the top one is the
	 * project itself, we are always within it) so two different tokens is 'nuff.
	**/
	private boolean skipToVST() throws java.io.IOException
	{
		this.reader.skipTo("VST", "TRACK");
		if(this.reader.getWord().equals("VST"))
			return true;
		if(this.reader.getWord().equals("TRACK"))
			this.reader.pushBack();
		return false;
	}
}

abstract class ReaperTokenizer
{
	private final java.io.BufferedReader reader;
	
	public enum Tokens {EOF, EOL, STRING, NUMBER, CHAR};
	
	public ReaperTokenizer(final java.io.BufferedReader br)
	{
		this.reader = br;
	}
	
	abstract public Tokens nextToken() throws java.io.IOException;
	abstract public int lineno();
	abstract public Tokens getToken();
	abstract public String getWord();
	abstract public void pushBack();
	abstract public Tokens skipTo(final String keyword) throws java.io.IOException;
	abstract public Tokens skipTo(final String keyword1, final String keyword2) throws java.io.IOException;
}
//-----------------------------------------------
class RprStreamTokenizer extends ReaperTokenizer
{
	private final java.io.StreamTokenizer reader;
	private int line_number = 0;
	private String word;
	private ReaperTokenizer.Tokens token;
	
	public RprStreamTokenizer(final java.io.BufferedReader br)
	{
		super(br);
		this.reader = new java.io.StreamTokenizer(br);
		this.reader.wordChars('_','_');
		this.reader.wordChars('{', '}');
	}

	@Override
	public Tokens getToken()
	{
		return this.token;
	}
	
	@Override
	public String getWord()
	{
		return this.word;
	}
	
	@Override
	public Tokens nextToken() throws java.io.IOException
	{
		int nxttoken = this.reader.nextToken();
		
		switch(nxttoken)
		{
			case StreamTokenizer.TT_EOF:	this.token = Tokens.EOF; this.word = "EOF"; break;
			case StreamTokenizer.TT_EOL:	this.token = Tokens.EOL; this.word = "EOL"; break;
			case StreamTokenizer.TT_NUMBER:	this.token = Tokens.NUMBER; this.word = Double.toString(this.reader.nval); break;
			case StreamTokenizer.TT_WORD:	this.token = Tokens.STRING; this.word = this.reader.sval; break;
			default: handleDefault(this.reader.toString());
		}

		this.line_number = reader.lineno();		
		return this.token;
	}

	@Override
	public int lineno()
	{
		return this.reader.lineno();
	}

	@Override
	public void pushBack()
	{
		this.reader.pushBack();
	}
	
	@Override
	public Tokens skipTo(final String keyword) throws java.io.IOException
	{
		while(this.nextToken() != Tokens.EOF)
			if(this.word.equals(keyword))
				return this.token;
		
		return this.token;
	}
	
	@Override
	public Tokens skipTo(final String keyword1, final String keyword2) throws java.io.IOException
	{
		while(this.nextToken() != Tokens.EOF)
			if(this.word.equals(keyword1) || this.word.equals(keyword2))
				return this.token;
		
		return this.token;		
	}
	
	@Override
	public String toString()
	{
		final StringBuffer buf = new StringBuffer();
		buf.append(this.line_number);
		buf.append(": ");
		buf.append(this.token.name());
		buf.append(", ");
		buf.append(this.word);
		return buf.toString();
	}
	
	/*
	 * Handles the default value coming from the switch
	 * StreamTokenizer treast all sorts of thinsg as unknown,, empty struings, for instance
	 * Thi sone trie to extract the token from StreamTokenizer.toString()
	 * This is a work around, since we do not know teh exact format of that string. The
	 * specs say that "The precise string returned is unspecified, although the following example can be considered typical:
	 * Token['a'], line 10"
	**/
	private void handleDefault(final String str)
	{
		String[] result = str.split("[\\[\\]]");
		// Here, result[1] should hold the string, but it can be single char or empty string
		this.word = result[1];
		if(this.word.length() == 1)
		{
			this.token = Tokens.CHAR;
		}
		else
		{
			this.token = Tokens.STRING;
		}
	}
}
/**
 *
 * @author Fabian
 */
public class ReaperreadeR
{
	private int track_num = 1;
	private final ReaperTokenizer reader;
	private final java.util.List<Track> track_list;
	
	public ReaperreadeR(final java.io.BufferedReader br)
	{
		this.reader = new RprStreamTokenizer(br);
		this.track_list = new java.util.LinkedList<>();
	}

	public void parse(final int full) throws java.io.IOException
	{
		while(this.reader.nextToken() != ReaperTokenizer.Tokens.EOF)
		{
			this.reader.pushBack();	// Must push this back, else we devour it and may miss a track
			
			if(full != 0)	// Do the real mambo...
			{
				if(skipToTrack())
				{
					final Track track = new Track(this.track_num++, this.reader);
					this.track_list.add(track);
				}
				else
					return;
			}
			else	// This is basically debug stuff
				System.out.println(this.reader.toString());
		}

		if(full == 0)	// Debug stuff
			System.out.println(this.reader.toString());
	}
	
	public void showtrackList()
	{
		for(final Track track : this.track_list)
		{
			System.out.println(track.toString());
		}
	}
	
	private boolean skipToTrack() throws java.io.IOException
	{
//		while(this.reader.nextToken() != ReaperTokenizer.Tokens.EOF)
//		{
//			if(this.reader.getWord().equals("TRACK"))
//				return true;
//		}
		return this.reader.skipTo("TRACK") != ReaperTokenizer.Tokens.EOF;
	}
	
	/**
	 * @param args the command line arguments, first arg assuemd to be the filename
	 * @throws java.io.IOException if the given filename cannot be found
	 */
	public static void main(String[] args) throws java.io.IOException
	{
		if(args.length < 1)
		{
			System.out.println("usage: ReaperreadeR <reaper project file> [<reaper project file> ...]");
			return;
		}
		
		for(final String filename : args)
		{
			System.out.println("Reaper project: " + filename);
			try(java.io.BufferedReader br = new java.io.BufferedReader(new java.io.FileReader(filename)))
			{
				final ReaperreadeR rr = new ReaperreadeR(br);
				rr.parse(42);	// 0 means print out all tokens, anything else means just manage the tracks
				rr.showtrackList();
			}
		}
	}
	
}
